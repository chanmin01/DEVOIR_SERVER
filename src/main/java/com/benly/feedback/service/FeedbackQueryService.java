package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackReportResponse;
import com.benly.feedback.dto.FeedbackStatusResponse;
import com.benly.feedback.entity.*;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.*;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.pt.dto.PtTaskContent;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.pt.service.PtContentCodec;
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
    private final SessionFormatResolver formatResolver;
    private final PtSessionRepository ptSessionRepository;
    private final PtContentCodec ptContentCodec;

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

        SessionFormat format = formatResolver.resolve(sessionId);
        List<Feedback> feedbacks = feedbackRepository.findWithQuestionBySessionId(sessionId);
        List<ScoreAxis> scoreAxes = scoreAxisRepository.findBySessionId(sessionId);
        List<Question> allQuestions = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId);
        List<Answer> allAnswers = answerReadRepository.findBySessionId(sessionId);

        Feedback presentation = feedbacks.stream().filter(Feedback::isPresentation).findFirst().orElse(null);
        boolean ptWithPresentation = format == SessionFormat.PT && presentation != null;

        AxisSet radarSet = ptWithPresentation
                ? AxisSet.PT
                : AxisSet.resolve(session.getStage(), session.getCompanyType());
        Radar radar = assembler.assembleRadar(radarSet, scoreAxes,
                ptWithPresentation ? CardType.PRESENTATION : null);
        List<Card> cards = assembler.assembleCards(feedbacks, allQuestions, allAnswers,
                format == SessionFormat.PT ? presentationMetaOf(sessionId) : null);

        return new FeedbackReportResponse(
                sessionId,
                new Meta(
                        format.name(),
                        session.getCompanyType(),
                        session.getStage(),
                        session.getCompanyName(),
                        session.getJobTitle(),
                        session.getCreatedAt().toString()
                ),
                new KeyCoaching(sf.getKeyCoaching(), sf.getCoachingAction()),
                sf.getTotalScore(),
                ptWithPresentation ? breakdownOf(presentation, feedbacks) : null,
                sf.getSummary(),
                radar,
                cards
        );
    }

    private FeedbackReportAssembler.PresentationMeta presentationMetaOf(Long sessionId) {
        return ptSessionRepository.findById(sessionId)
                .map(pt -> {
                    PtTaskContent task = ptContentCodec.content(pt.getTask().getContentJson());
                    return new FeedbackReportAssembler.PresentationMeta(
                            task.title(),
                            pt.getPresentationTranscript(),
                            pt.getPresentationDurationSec(),
                            pt.getTrack() == null ? null : pt.getTrack().name());
                })
                .orElse(null);
    }

    private ScoreBreakdown breakdownOf(Feedback presentation, List<Feedback> feedbacks) {
        List<Integer> qaScores = feedbacks.stream()
                .filter(f -> !f.isPresentation())
                .map(f -> f.getTopicScore() == null ? 0 : f.getTopicScore())
                .toList();
        int presentationScore = presentation.getTopicScore() == null ? 0 : presentation.getTopicScore();

        TotalScoreCalculator.Breakdown b = TotalScoreCalculator
                .calculate(SessionFormat.PT, presentationScore, qaScores).breakdown();
        return new ScoreBreakdown(b.presentation(), b.qaAverage(), b.presentationWeight(), b.qaWeight());
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
