package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.SessionFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackCommandServiceTest {

    @Mock private FeedbackScoringClient scoringClient;
    @Mock private FeedbackScoringTx scoringTx;

    @InjectMocks
    private FeedbackCommandService service;

    private static final Long SESSION_ID = 8L;
    private static final LocalDateTime ATTEMPT = LocalDateTime.of(2026, 11, 3, 12, 0);

    private ScoringPlan planWith(MainQuestionInput input) {
        AxisSet axisSet = AxisSet.PERSONALITY;
        ScoringContext ctx = new ScoringContext("IT", "PERSONALITY", "백엔드");
        return new ScoringPlan(
                axisSet.axes(), ctx, axisSet,
                List.of(new ScoringPlan.MainInput(100L, input)),
                SessionFormat.QA);
    }

    /** 모든 축을 100점으로 채운 결과 (가중합도 100이 됨) */
    private MainQuestionScore allHundred(List<Axis> axes) {
        Map<String, Integer> m = new HashMap<>();
        axes.forEach(a -> m.put(a.code(), 100));
        return new MainQuestionScore("제목", m, null);
    }

    private void givenSummary() {
        given(scoringClient.summarize(any(), anyInt(), anyList(), any(), anyInt()))
                .willReturn(new SessionSummary("총평", "약점", "행동"));
    }

    private void answeredPlan() {
        MainQuestionInput answered = new MainQuestionInput(1, "메인", "답변", List.of());
        ScoringPlan plan = planWith(answered);
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(plan);
        given(scoringClient.scoreMainQuestion(any(), any(), any()))
                .willReturn(allHundred(plan.axes()));
        givenSummary();
    }

    @Test
    @DisplayName("답변이 있는 문항은 AI로 채점하고 결과를 저장한다")
    void scoreAnsweredTopic() {
        // given
        answeredPlan();
        given(scoringTx.saveResults(eq(SESSION_ID), eq(ATTEMPT), anyInt(), anyList(), any(), any()))
                .willReturn(true);

        // when
        service.score(SESSION_ID, ATTEMPT);

        // then
        verify(scoringClient, times(1)).scoreMainQuestion(any(), any(), any());

        ArgumentCaptor<List<ScoredTopic>> captor = ArgumentCaptor.forClass(List.class);
        verify(scoringTx).saveResults(eq(SESSION_ID), eq(ATTEMPT), eq(100), captor.capture(), isNull(), any());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).topicScore()).isEqualTo(100);
        verify(scoringTx, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("통째로 건너뛴 문항은 AI를 부르지 않고 0점으로 저장한다")
    void skipTopicWithoutAiCall() {
        // given
        MainQuestionInput skipped = new MainQuestionInput(1, "메인질문", null, List.of());
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(planWith(skipped));
        givenSummary();
        given(scoringTx.saveResults(eq(SESSION_ID), eq(ATTEMPT), anyInt(), anyList(), any(), any()))
                .willReturn(true);

        // when
        service.score(SESSION_ID, ATTEMPT);

        // then
        verify(scoringClient, never()).scoreMainQuestion(any(), any(), any());
        verify(scoringTx).saveResults(eq(SESSION_ID), eq(ATTEMPT), eq(0), anyList(), any(), any());
    }

    @Test
    @DisplayName("채점 중 예외가 나면 저장 대신 내 시도만 실패 처리한다")
    void markFailedOnError() {
        // given
        MainQuestionInput answered = new MainQuestionInput(1, "메인", "답변", List.of());
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(planWith(answered));
        given(scoringClient.scoreMainQuestion(any(), any(), any()))
                .willThrow(new RuntimeException("AI 호출 실패"));

        // when
        service.score(SESSION_ID, ATTEMPT);

        // then
        verify(scoringTx).markFailed(SESSION_ID, ATTEMPT);
        verify(scoringTx, never()).saveResults(anyLong(), any(), anyInt(), anyList(), any(), any());
    }

    @Test
    @DisplayName("새 채점이 이미 시작됐으면 결과를 버리고 실패 처리도 하지 않는다")
    void discardWhenAttemptReplaced() {
        // given
        answeredPlan();
        given(scoringTx.saveResults(eq(SESSION_ID), eq(ATTEMPT), anyInt(), anyList(), any(), any()))
                .willReturn(false);

        // when
        service.score(SESSION_ID, ATTEMPT);

        // then
        verify(scoringTx, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("PT에서 발표가 기록되지 않으면 AI를 부르지 않고 발표 0점, 질의응답 70% 비율로 총점을 낸다")
    void ptMissingPresentation() {
        // given
        AxisSet axisSet = AxisSet.PERSONALITY;
        ScoringContext ctx = new ScoringContext("FINANCE_IT", "PT", "백엔드");
        PresentationInput noPresentation = new PresentationInput(
                "과제", "문제", List.of(), "AI", "AI", "가이드",
                List.of("요구1", "요구2"), null, null);
        ScoringPlan plan = new ScoringPlan(
                axisSet.axes(), ctx, axisSet,
                List.of(new ScoringPlan.MainInput(100L,
                        new MainQuestionInput(1, "메인", "답변", List.of()))),
                SessionFormat.PT,
                noPresentation);

        given(scoringTx.loadPlan(SESSION_ID)).willReturn(plan);
        given(scoringClient.scoreMainQuestion(any(), any(), any()))
                .willReturn(allHundred(plan.axes()));
        givenSummary();
        given(scoringTx.saveResults(eq(SESSION_ID), eq(ATTEMPT), anyInt(), anyList(), any(), any()))
                .willReturn(true);

        // when
        service.score(SESSION_ID, ATTEMPT);

        // then
        verify(scoringClient, never()).scorePresentation(any(), any(), any());

        ArgumentCaptor<ScoredPresentation> captor = ArgumentCaptor.forClass(ScoredPresentation.class);
        verify(scoringTx).saveResults(eq(SESSION_ID), eq(ATTEMPT), eq(70), anyList(), captor.capture(), any());
        assertThat(captor.getValue().topicScore()).isZero();
        assertThat(captor.getValue().score().content().requirements())
                .extracting("status")
                .containsOnly("MISSING");
    }
}