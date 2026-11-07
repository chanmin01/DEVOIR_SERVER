package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.CardType;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.ScoreAxis;
import com.benly.question.entity.Answer;
import com.benly.question.entity.AnswerType;
import com.benly.question.entity.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.benly.feedback.dto.FeedbackReportResponse.*;

@Component
@RequiredArgsConstructor
public class FeedbackReportAssembler {

    private final FeedbackContentParser contentParser;

    /**
     * radar 조립: 대상 카드의 ScoreAxis를 축별로 평균내 AxisSet 순서로 정렬.
     * onlyCardType이 있으면 그 카드 종류의 축만 쓴다. PT는 PRESENTATION만.
     */
    public Radar assembleRadar(AxisSet axisSet, List<ScoreAxis> scoreAxes, CardType onlyCardType) {
        Map<String, List<Integer>> byAxis = new HashMap<>();
        for (ScoreAxis sa : scoreAxes) {
            if (onlyCardType != null && sa.getFeedback().getCardType() != onlyCardType) {
                continue;
            }
            byAxis.computeIfAbsent(sa.getAxis(), k -> new ArrayList<>()).add(sa.getScore());
        }
        List<Radar.Axis> axes = new ArrayList<>();
        for (Axis def : axisSet.axes()) {
            List<Integer> scores = byAxis.getOrDefault(def.code(), List.of());
            int avg = scores.isEmpty() ? 0
                    : Math.round((float) scores.stream().mapToInt(Integer::intValue).sum() / scores.size());
            axes.add(new Radar.Axis(def.code(), def.label(), avg));
        }
        return new Radar(axisSet.name(), axes);
    }

    public Radar assembleRadar(AxisSet axisSet, List<ScoreAxis> scoreAxes) {
        return assembleRadar(axisSet, scoreAxes, null);
    }

    /** 발표 카드에 붙일 PT 정보. QA면 null */
    public record PresentationMeta(String taskTitle, String transcript, Integer durationSeconds, String track) {
    }

    public List<Card> assembleCards(
            List<Feedback> feedbacks,
            List<Question> allQuestions,
            List<Answer> allAnswers) {
        return assembleCards(feedbacks, allQuestions, allAnswers, null);
    }

    /**
     * cards 조립: 질문 기준으로 만든다. 아직 피드백 행이 없는 질문도 SCORING 카드로 자리를 잡는다.
     * PT면 발표 카드가 맨 앞, 이어서 메인 질문 순서대로 질문 카드와 그 꼬리들.
     */
    public List<Card> assembleCards(
            List<Feedback> feedbacks,
            List<Question> allQuestions,
            List<Answer> allAnswers,
            PresentationMeta meta) {

        Map<Long, Answer> answerByQuestion = allAnswers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

        Map<Long, List<Question>> tailsByParent = allQuestions.stream()
                .filter(q -> q.getParent() != null)
                .collect(Collectors.groupingBy(q -> q.getParent().getId()));

        Map<Long, Feedback> cardByQuestion = feedbacks.stream()
                .filter(f -> !f.isPresentation() && f.getQuestion() != null)
                .collect(Collectors.toMap(f -> f.getQuestion().getId(), f -> f, (a, b) -> a));

        List<Card> cards = new ArrayList<>();

        if (meta != null) {
            Feedback presentation = feedbacks.stream()
                    .filter(Feedback::isPresentation).findFirst().orElse(null);
            cards.add(presentationCard(presentation, meta));
        }

        allQuestions.stream()
                .filter(Question::isMain)
                .sorted(Comparator.comparing(Question::getSeq))
                .forEach(main -> cards.add(questionCard(
                        main, cardByQuestion.get(main.getId()), tailsByParent, answerByQuestion)));

        return cards;
    }

    private Card questionCard(Question main, Feedback fb,
                              Map<Long, List<Question>> tailsByParent,
                              Map<Long, Answer> answerByQuestion) {
        Answer answer = answerByQuestion.get(main.getId());
        boolean skipped = isSkipped(answer);
        FeedbackContent content = completedContent(fb);

        return new Card(
                CardType.QA.name(),
                statusOf(fb),
                main.getId(),
                main.getSeq(),
                main.getShortTitle(),
                scoreOf(fb),
                main.getContent(),
                skipped ? null : transcriptOf(answer),
                skipped,
                null,
                null,
                List.of(),
                content != null ? content.good() : null,
                content != null ? content.weak() : null,
                content != null ? content.next() : null,
                content != null ? content.weakAxis() : null,
                improvedAnswerOf(content),
                null,
                assembleTails(main.getId(), tailsByParent, answerByQuestion, content)
        );
    }

    private Card presentationCard(Feedback fb, PresentationMeta meta) {
        String transcript = meta.transcript();
        boolean skipped = transcript == null || transcript.isBlank();
        String title = meta.taskTitle();
        FeedbackContent content = completedContent(fb);

        return new Card(
                CardType.PRESENTATION.name(),
                statusOf(fb),
                null,
                0,
                title,
                scoreOf(fb),
                title,
                skipped ? null : transcript,
                skipped,
                meta.durationSeconds(),
                meta.track(),
                requirementsOf(content),
                skipped || content == null ? null : content.good(),
                skipped || content == null ? null : content.weak(),
                skipped || content == null ? null : content.next(),
                skipped || content == null ? null : content.weakAxis(),
                null,
                skipped ? null : outlineOf(content),
                List.of()
        );
    }

    /** 피드백 행이 아직 없으면 채점 대기 중인 카드로 본다 */
    private String statusOf(Feedback fb) {
        return fb == null ? FeedbackStatus.SCORING.name() : fb.getScoringStatus().name();
    }

    private Integer scoreOf(Feedback fb) {
        return fb != null && fb.isCompleted() ? fb.getTopicScore() : null;
    }

    /** 채점이 끝난 카드만 내용을 푼다. 채점 중이거나 실패한 카드는 피드백 필드를 비운다 */
    private FeedbackContent completedContent(Feedback fb) {
        return fb != null && fb.isCompleted() ? contentParser.parse(fb.getContent()) : null;
    }

    private List<Requirement> requirementsOf(FeedbackContent content) {
        if (content == null || content.requirements() == null) {
            return List.of();
        }
        return content.requirements().stream()
                .map(r -> new Requirement(r.requirement(), r.status(), r.evidence()))
                .toList();
    }

    private ImprovedOutline outlineOf(FeedbackContent content) {
        if (content == null || content.improvedOutline() == null) {
            return null;
        }
        return new ImprovedOutline(
                content.improvedOutline().before() == null ? List.of() : content.improvedOutline().before(),
                content.improvedOutline().after() == null ? List.of() : content.improvedOutline().after());
    }

    private List<Tail> assembleTails(
            Long mainId,
            Map<Long, List<Question>> tailsByParent,
            Map<Long, Answer> answerByQuestion,
            FeedbackContent content) {

        List<Question> tailQuestions = new ArrayList<>(tailsByParent.getOrDefault(mainId, List.of()));
        tailQuestions.sort(Comparator.comparing(Question::getSeq));

        List<FeedbackContent.TailContent> tailContents =
                (content != null && content.tails() != null) ? content.tails() : List.of();

        List<Tail> tails = new ArrayList<>();
        for (int i = 0; i < tailQuestions.size(); i++) {
            Question tq = tailQuestions.get(i);
            Answer answer = answerByQuestion.get(tq.getId());
            boolean skipped = isSkipped(answer);
            FeedbackContent.TailContent tc =
                    (!skipped && i < tailContents.size()) ? tailContents.get(i) : null;

            tails.add(new Tail(
                    tq.getStrategy() == null ? null : tq.getStrategy().name(),
                    tq.getContent(),
                    skipped ? null : transcriptOf(answer),
                    skipped,
                    tc != null ? tc.good() : null,
                    tc != null ? tc.weak() : null,
                    tc != null ? tc.next() : null,
                    tc != null ? tc.weakAxis() : null,
                    tc != null ? tc.improvedAfter() : null
            ));
        }
        return tails;
    }

    private boolean isSkipped(Answer answer) {
        return answer != null && answer.getInputType() == AnswerType.SKIP;
    }

    private String transcriptOf(Answer answer) {
        return answer != null ? answer.getTranscript() : null;
    }

    private ImprovedAnswer improvedAnswerOf(FeedbackContent content) {
        if (content == null || content.improvedAnswer() == null) {
            return null;
        }
        return new ImprovedAnswer(
                content.improvedAnswer().before(),
                content.improvedAnswer().after());
    }
}
