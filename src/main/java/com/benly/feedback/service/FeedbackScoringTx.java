package com.benly.feedback.service;

import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.ScoreAxis;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.*;
import com.benly.global.exception.BusinessException;
import com.benly.question.entity.Answer;
import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackScoringTx {

    private final SessionReadRepository sessionReadRepository;
    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final FeedbackRepository feedbackRepository;
    private final ScoreAxisRepository scoreAxisRepository;
    private final QuestionReadRepository questionReadRepository;
    private final AnswerReadRepository answerReadRepository;
    private final FeedbackContentParser contentParser;

    @Transactional(readOnly = true)
    public ScoringPlan loadPlan(Long sessionId) {
        Session session = sessionReadRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.SESSION_NOT_FOUND));

        AxisSet axisSet = AxisSet.resolve(session.getStage(), session.getCompanyType());
        ScoringContext ctx = ScoringContext.from(session);

        List<Question> all = questionReadRepository.findBySession_IdOrderBySeqAsc(sessionId);

        Map<Long, String> transcripts = new HashMap<>();
        for (Answer a : answerReadRepository.findBySessionId(sessionId)) {
            if (a.getQuestion() != null) {
                transcripts.put(a.getQuestion().getId(), a.getTranscript());
            }
        }

        Map<Long, List<Question>> tailsByParent = all.stream()
                .filter(q -> q.getParent() != null)
                .collect(Collectors.groupingBy(q -> q.getParent().getId()));

        List<ScoringPlan.MainInput> mains = all.stream()
                .filter(Question::isMain)
                .sorted(Comparator.comparing(Question::getSeq))
                .map(m -> new ScoringPlan.MainInput(m.getId(), toInput(m, tailsByParent, transcripts)))
                .toList();

        return new ScoringPlan(axisSet.axes(), ctx, axisSet, mains);
    }

    private MainQuestionInput toInput(Question main,
                                      Map<Long, List<Question>> tailsByParent,
                                      Map<Long, String> transcripts) {
        List<Question> tqs = new ArrayList<>(tailsByParent.getOrDefault(main.getId(), List.of()));
        tqs.sort(Comparator.comparing(Question::getSeq));

        List<TailInput> tails = tqs.stream()
                .map(t -> new TailInput(
                        t.getStrategy() == null ? null : t.getStrategy().getLabel(),
                        t.getContent(),
                        transcripts.get(t.getId())))
                .toList();

        return new MainQuestionInput(
                main.getSeq(), main.getContent(), transcripts.get(main.getId()), tails);
    }

    @Transactional
    public void saveResults(Long sessionId, int totalScore,
                            List<ScoredTopic> topics, SessionSummary summary) {
        scoreAxisRepository.deleteBySessionId(sessionId);
        feedbackRepository.deleteBySessionId(sessionId);

        topics.forEach(this::persistTopic);

        SessionFeedback sf = sessionFeedbackRepository.findBySession_Id(sessionId)
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.REPORT_NOT_FOUND));
        sf.complete(totalScore, summary.summary(),
                summary.keyCoachingWeakness(), summary.keyCoachingAction());
    }

    private void persistTopic(ScoredTopic topic) {
        Question main = questionReadRepository.findById(topic.mainQuestionId())
                .orElseThrow(() -> new BusinessException(FeedbackErrorCode.QUESTION_NOT_FOUND));

        main.assignShortTitle(topic.score().shortTitle());

        Feedback feedback = feedbackRepository.save(
                Feedback.create(main, contentParser.toJson(topic.score().content()), topic.topicScore()));

        topic.score().axisScores()
                .forEach((code, value) -> scoreAxisRepository.save(ScoreAxis.create(feedback, code, value)));
    }

    /** 실패 처리 */
    @Transactional
    public void markFailed(Long sessionId) {
        sessionFeedbackRepository.findBySession_Id(sessionId)
                .ifPresent(SessionFeedback::fail);
    }
}