package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackScoringResponse;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.event.ScoringRequestedEvent;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.SessionFeedbackRepository;
import com.benly.feedback.repository.SessionReadRepository;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.session.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FeedbackRequestService {

    private final SessionReadRepository sessionReadRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StaleTaskPolicy staleTaskPolicy;

    @Transactional
    public FeedbackScoringResponse requestScoring(Long userId, Long sessionId) {
        Session session = sessionReadRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(FeedbackErrorCode.SESSION_FORBIDDEN);
        }

        prepareScoring(session);

        eventPublisher.publishEvent(new ScoringRequestedEvent(sessionId));
        return FeedbackScoringResponse.from(sessionId);
    }

    private void prepareScoring(Session session) {
        switch (session.getStatus()) {
            case COMPLETED -> createScoringRecord(session);
            case ANALYZING -> resumeOrRescore(session);
            default -> throw new BusinessException(FeedbackErrorCode.SESSION_NOT_FINISHED);
        }
    }

    private void createScoringRecord(Session session) {
        session.markAnalyzing();
        insertScoringRecord(session);
    }

    private void insertScoringRecord(Session session) {
        try {
            sessionFeedbackRepository.saveAndFlush(SessionFeedback.startScoring(session));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
    }

    private void resumeOrRescore(Session session) {
        Long sessionId = session.getId();
        SessionFeedback sf = sessionFeedbackRepository.findBySession_Id(sessionId).orElse(null);

        if (sf == null) {
            insertScoringRecord(session);
            return;
        }
        if (sf.isCompleted()) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
        if (sf.isScoring()) {
            restartIfStale(sessionId);
            return;
        }
        int updated = sessionFeedbackRepository.restartScoring(
                sessionId, FeedbackStatus.FAILED, FeedbackStatus.SCORING, LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
    }

    // 채점 중인데 기준 시간을 넘겼으면 멈춘 작업으로 보고 다시 채점한다
    private void restartIfStale(Long sessionId) {
        int restarted = sessionFeedbackRepository.restartStaleScoring(
                sessionId, staleTaskPolicy.scoringThreshold(), LocalDateTime.now());
        if (restarted == 0) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
    }
}
