package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardScorerTest {

    @Mock private FeedbackScoringClient scoringClient;

    @InjectMocks
    private CardScorer scorer;

    private static final ScoringContext CTX = new ScoringContext("IT", "PERSONALITY", "백엔드");

    private Map<String, Integer> allHundred(List<Axis> axes) {
        Map<String, Integer> m = new HashMap<>();
        axes.forEach(a -> m.put(a.code(), 100));
        return m;
    }

    @Test
    @DisplayName("통째로 건너뛴 주제는 Claude를 부르지 않고 0점이다")
    void skippedTopicWithoutCall() {
        MainQuestionInput in = new MainQuestionInput(1, "메인", null, List.of());

        ScoredTopic topic = scorer.scoreTopic(100L, AxisSet.PERSONALITY, CTX, in);

        verify(scoringClient, never()).scoreMainQuestion(any(), any(), any());
        assertThat(topic.topicScore()).isZero();
        assertThat(topic.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일부만 답하면 답한 비율만큼 점수를 줄인다")
    void partialAnswerScaled() {
        MainQuestionInput in = new MainQuestionInput(1, "메인", "답변",
                List.of(new TailInput("WHY", "꼬리", null)));
        given(scoringClient.scoreMainQuestion(any(), any(), any()))
                .willReturn(new MainQuestionScore("제목", allHundred(AxisSet.PERSONALITY.axes()), null));

        ScoredTopic topic = scorer.scoreTopic(100L, AxisSet.PERSONALITY, CTX, in);

        assertThat(topic.topicScore()).isEqualTo(50);
        assertThat(topic.skippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("발표가 기록되지 않으면 Claude를 부르지 않고 0점, 요구사항은 전부 MISSING이다")
    void missingPresentationWithoutCall() {
        PresentationInput in = new PresentationInput(
                "과제", "설명", List.of(), "문제", List.of(), List.of(), List.of(),
                "AI", "AI", "가이드", List.of("요구1", "요구2"), null, " ");

        ScoredPresentation presentation = scorer.scorePresentation(CTX, in);

        verify(scoringClient, never()).scorePresentation(any(), any(), any());
        assertThat(presentation.topicScore()).isZero();
        assertThat(presentation.score().content().requirements())
                .extracting("status")
                .containsOnly("MISSING");
    }
}
