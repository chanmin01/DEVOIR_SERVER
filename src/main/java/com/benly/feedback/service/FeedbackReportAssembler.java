package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.ScoreAxis;
import com.benly.question.entity.Answer;
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
     * radar 조립: ScoreAxis 30개를 축별 평균내여 AxisSet 순서로 정렬.
     */
    public Radar assembleRadar(AxisSet axisSet, List<ScoreAxis> scoreAxes) {
        Map<String, List<Integer>> byAxis = new HashMap<>();
        for (ScoreAxis sa : scoreAxes) {
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

    /**
     * cards 조립: 메인 Feedback 5개 → Card. 각 카드에 꼬리(tails) 묶기.
     */
    public List<Card> assembleCards(
            List<Feedback> feedbacks,          // 메인 5개 (question fetch join됨)
            List<Question> allQuestions,       // 세션 전체 질문 (메인+꼬리)
            List<Answer> allAnswers) {         // 세션 전체 답변

        // questionId → Answer 맵
        Map<Long, Answer> answerByQuestion = allAnswers.stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

        // parentId → 꼬리 질문들 맵 (부모별 꼬리 묶기, seq 순)
        Map<Long, List<Question>> tailsByParent = allQuestions.stream()
                .filter(q -> q.getParent() != null)
                .collect(Collectors.groupingBy(q -> q.getParent().getId()));

        List<Card> cards = new ArrayList<>();
        for (Feedback fb : feedbacks) {
            Question main = fb.getQuestion();
            FeedbackContent content = contentParser.parse(fb.getContent());

            cards.add(new Card(
                    main.getId(),
                    main.getSeq(),
                    main.getShortTitle(),
                    fb.getTopicScore(),
                    main.getContent(),
                    transcriptOf(answerByQuestion.get(main.getId())),
                    content != null ? content.good() : null,
                    content != null ? content.weak() : null,
                    content != null ? content.next() : null,
                    content != null ? content.weakAxis() : null,
                    improvedAnswerOf(content),
                    assembleTails(main.getId(), tailsByParent, answerByQuestion, content)
            ));
        }
        return cards;
    }

    /**
     * 한 메인의 꼬리들을 조립
     */
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
            FeedbackContent.TailContent tc = (i < tailContents.size()) ? tailContents.get(i) : null;

            tails.add(new Tail(
                    tq.getStrategy() == null ? null : tq.getStrategy().name(),
                    tq.getContent(),
                    transcriptOf(answerByQuestion.get(tq.getId())),
                    tc != null ? tc.good() : null,
                    tc != null ? tc.weak() : null,
                    tc != null ? tc.next() : null,
                    tc != null ? tc.weakAxis() : null,
                    tc != null ? tc.improvedAfter() : null
            ));
        }
        return tails;
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
