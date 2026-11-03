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

        LocalDateTime attempt = prepareScoring(session);

        eventPublisher.publishEvent(new ScoringRequestedEvent(sessionId, attempt));
        return FeedbackScoringResponse.from(sessionId);
    }

    private LocalDateTime prepareScoring(Session session) {
        return switch (session.getStatus()) {
            case COMPLETED -> createScoringRecord(session);
            case ANALYZING -> resumeOrRescore(session);
            default -> throw new BusinessException(FeedbackErrorCode.SESSION_NOT_FINISHED);
        };
    }

    private LocalDateTime createScoringRecord(Session session) {
        session.markAnalyzing();
        return insertScoringRecord(session);
    }

    private LocalDateTime insertScoringRecord(Session session) {
        SessionFeedback sf = SessionFeedback.startScoring(session);
        try {
            sessionFeedbackRepository.saveAndFlush(sf);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
        return sf.getScoringStartedAt();
    }

    // 실패한 채점은 바로, 채점 중인 건은 기준 시간을 넘겼을 때만 새 시도로 다시 시작
    private LocalDateTime resumeOrRescore(Session session) {
        Long sessionId = session.getId();
        SessionFeedback sf = sessionFeedbackRepository.findBySession_Id(sessionId).orElse(null);

        if (sf == null) {
            return insertScoringRecord(session);
        }
        if (sf.isCompleted()) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }

        LocalDateTime attempt = SessionFeedback.newAttemptTime();
        int updated = sf.isScoring()
                ? sessionFeedbackRepository.restartStaleScoring(
                sessionId, staleTaskPolicy.scoringThreshold(), attempt)
                : sessionFeedbackRepository.restartScoring(
                sessionId, FeedbackStatus.FAILED, FeedbackStatus.SCORING, attempt);
        if (updated == 0) {
            throw new BusinessException(FeedbackErrorCode.ALREADY_SCORED);
        }
        return attempt;
    }
}
