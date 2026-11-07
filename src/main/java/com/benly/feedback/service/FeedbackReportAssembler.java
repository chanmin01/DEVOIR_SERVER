package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.CardType;
import com.benly.feedback.entity.Feedback;
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

    /**
     * cards 조립: 발표 카드가 있으면 맨 앞, 이어서 질문 카드와 그 꼬리들.
     */
    public List<Card> assembleCards(
            List<Feedback> feedbacks,
            List<Question> allQuestions,
            List<Answer> allAnswers) {

        Map<Long, Answer> answerByQuestion = allAnswers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

        Map<Long, List<Question>> tailsByParent = allQuestions.stream()
                .filter(q -> q.getParent() != null)
                .collect(Collectors.groupingBy(q -> q.getParent().getId()));

        List<Card> cards = new ArrayList<>();
        for (Feedback fb : feedbacks) {
            FeedbackContent content = contentParser.parse(fb.getContent());
            cards.add(fb.isPresentation()
                    ? presentationCard(fb, content)
                    : questionCard(fb, content, tailsByParent, answerByQuestion));
        }
        return cards;
    }

    private Card questionCard(Feedback fb, FeedbackContent content,
                              Map<Long, List<Question>> tailsByParent,
                              Map<Long, Answer> answerByQuestion) {
        Question main = fb.getQuestion();
        Answer answer = answerByQuestion.get(main.getId());
        boolean skipped = isSkipped(answer);

        return new Card(
                CardType.QA.name(),
                main.getId(),
                main.getSeq(),
                main.getShortTitle(),
                fb.getTopicScore(),
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

    // 발표 카드 세부 항목은 PT 채점 이슈에서 채운다
    private Card presentationCard(Feedback fb, FeedbackContent content) {
        return new Card(
                CardType.PRESENTATION.name(),
                null,
                0,
                null,
                fb.getTopicScore(),
                null,
                null,
                false,
                null,
                null,
                List.of(),
                content != null ? content.good() : null,
                content != null ? content.weak() : null,
                content != null ? content.next() : null,
                content != null ? content.weakAxis() : null,
                null,
                null,
                List.of()
        );
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
