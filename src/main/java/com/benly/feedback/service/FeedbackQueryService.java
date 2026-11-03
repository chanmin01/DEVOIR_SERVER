package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackReportResponse;
import com.benly.feedback.dto.FeedbackStatusResponse;
import com.benly.feedback.entity.*;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.*;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.question.entity.Answer;
import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.benly.feedback.dto.FeedbackReportResponse.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackQueryService {

    private final SessionReadRepository sessionReadRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final FeedbackRepository feedbackRepository;
    private final ScoreAxisRepository scoreAxisRepository;
    private final QuestionReadRepository questionReadRepository;
    private final AnswerReadRepository answerReadRepository;
    private final FeedbackReportAssembler assembler;
    private final StaleTaskPolicy staleTaskPolicy;

    public FeedbackStatusResponse getStatus(Long userId, Long sessionId) {
        validateOwnerShip(userId, sessionId);

        SessionFeedback sf = sessionFeedbackRepository.findBySession_Id(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.REPORT_NOT_FOUND));

        FeedbackStatus status = sf.isStale(staleTaskPolicy.scoringThreshold())
                ? FeedbackStatus.FAILED
                : sf.getStatus();
        return FeedbackStatusResponse.of(sessionId, status);
    }

    public FeedbackReportResponse getReport(Long userId, Long sessionId) {
        Session session = validateOwnerShip(userId, sessionId);

        SessionFeedback sf = sessionFeedbackRepository.findBySession_Id(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.REPORT_NOT_FOUND));
        if (!sf.isCompleted()) {
            throw new BusinessException(FeedbackErrorCode.REPORT_NOT_FOUND);
        }

        List<Feedback> feedbacks = feedbackRepository.findWithQuestionBySessionId(sessionId);
        List<ScoreAxis> scoreAxes = scoreAxisRepository.findBySessionId(sessionId);
        List<Question> allQuestions = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId);
        List<Answer> allAnswers = answerReadRepository.findBySessionId(sessionId);

        AxisSet axisSet = AxisSet.resolve(session.getStage(), session.getCompanyType());
        Radar radar = assembler.assembleRadar(axisSet, scoreAxes);
        List<Card> cards = assembler.assembleCards(feedbacks, allQuestions, allAnswers);

        return new FeedbackReportResponse(
                sessionId,
                new Meta(
                        session.getCompanyType(),
                        session.getStage(),
                        session.getCompanyName(),
                        session.getJobTitle(),
                        session.getCreatedAt().toString()
                ),
                new KeyCoaching(sf.getKeyCoaching(), sf.getCoachingAction()),
                sf.getTotalScore(),
                sf.getSummary(),
                radar,
                cards,
                null
        );
    }


    private Session validateOwnerShip(Long userId, Long sessionId) {
        Session session = sessionReadRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));
        if (!session.getUser().getId().equals(userId)) {
            throw new BusinessException(FeedbackErrorCode.SESSION_FORBIDDEN);
        }
        return session;
    }
}
