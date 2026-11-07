package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.CardSnapshot;
import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackCommandServiceTest {

    @Mock private FeedbackScoringClient scoringClient;
    @Mock private FeedbackScoringTx scoringTx;
    @Mock private CardScoringService cardScoringService;

    @InjectMocks
    private FeedbackCommandService service;

    private static final Long SESSION_ID = 8L;
    private static final Long MAIN_ID = 100L;
    private static final LocalDateTime ATTEMPT = LocalDateTime.of(2026, 11, 3, 12, 0);

    private static final FeedbackContent CONTENT =
            new FeedbackContent("잘함", "아쉬움", "다음", "약한 축", null, List.of());

    private ScoringPlan qaPlan() {
        AxisSet axisSet = AxisSet.PERSONALITY;
        return new ScoringPlan(
                axisSet.axes(), new ScoringContext("IT", "PERSONALITY", "백엔드"), axisSet,
                List.of(new ScoringPlan.MainInput(MAIN_ID, new MainQuestionInput(1, "메인", "답변", List.of()))),
                SessionFormat.QA);
    }

    private ScoringPlan ptPlan() {
        AxisSet axisSet = AxisSet.TECHNICAL;
        PresentationInput presentation = new PresentationInput(
                "과제", "설명", List.of(), "문제", List.of(), List.of(), List.of(),
                "AI", "AI", "가이드", List.of("요구1"), null, null);
        return new ScoringPlan(
                axisSet.axes(), new ScoringContext("FINANCE_IT", "TECHNICAL", "백엔드"), axisSet,
                List.of(new ScoringPlan.MainInput(MAIN_ID, new MainQuestionInput(1, "메인", "답변", List.of()))),
                SessionFormat.PT, presentation);
    }

    private CardSnapshot qaCard(FeedbackStatus status, boolean inProgress, Integer score) {
        return new CardSnapshot(MAIN_ID, false, status, inProgress, score, "제목",
                status == FeedbackStatus.COMPLETED ? CONTENT : null);
    }

    private CardSnapshot presentationCard(Integer score) {
        return new CardSnapshot(null, true, FeedbackStatus.COMPLETED, false, score, null, CONTENT);
    }

    private void givenSummaryAndSave(boolean saved) {
        given(scoringClient.summarize(any(), anyInt(), anyList(), any(), anyInt()))
                .willReturn(new SessionSummary("총평", "약점", "행동"));
        given(scoringTx.finishSession(eq(SESSION_ID), eq(ATTEMPT), anyInt(), any())).willReturn(saved);
    }

    @Test
    @DisplayName("미리 채점이 끝난 카드는 다시 채점하지 않고 총평만 만든다")
    void skipPreScoredCards() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(List.of(qaCard(FeedbackStatus.COMPLETED, false, 80)));
        givenSummaryAndSave(true);

        service.score(SESSION_ID, ATTEMPT);

        verify(cardScoringService, never()).scoreTopic(anyLong());
        verify(scoringTx).finishSession(eq(SESSION_ID), eq(ATTEMPT), eq(80), any());
        verify(scoringTx, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("카드가 없으면 그 카드만 채점한 뒤 마무리한다")
    void scoreMissingCard() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(
                List.of(),
                List.of(qaCard(FeedbackStatus.COMPLETED, false, 70)));
        givenSummaryAndSave(true);

        service.score(SESSION_ID, ATTEMPT);

        verify(cardScoringService, times(1)).scoreTopic(MAIN_ID);
        verify(scoringTx).finishSession(eq(SESSION_ID), eq(ATTEMPT), eq(70), any());
    }

    @Test
    @DisplayName("같은 카드가 계속 실패하면 세션 채점을 실패로 끝낸다")
    void failWhenCardKeepsFailing() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(List.of(qaCard(FeedbackStatus.FAILED, false, null)));

        service.score(SESSION_ID, ATTEMPT);

        verify(cardScoringService, times(FeedbackCommandService.MAX_TRIES_PER_CARD)).scoreTopic(MAIN_ID);
        verify(scoringTx).markFailed(SESSION_ID, ATTEMPT);
        verify(scoringTx, never()).finishSession(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("새 채점이 이미 시작됐으면 결과를 버리고 실패 처리도 하지 않는다")
    void discardWhenAttemptReplaced() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(List.of(qaCard(FeedbackStatus.COMPLETED, false, 80)));
        givenSummaryAndSave(false);

        service.score(SESSION_ID, ATTEMPT);

        verify(scoringTx, never()).markFailed(anyLong(), any());
    }

    @Test
    @DisplayName("PT는 발표 카드 30, 질의응답 70 비율로 총점을 낸다")
    void ptTotalScore() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(ptPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(List.of(
                presentationCard(0),
                qaCard(FeedbackStatus.COMPLETED, false, 100)));
        givenSummaryAndSave(true);

        service.score(SESSION_ID, ATTEMPT);

        verify(cardScoringService, never()).scorePresentation(anyLong());
        verify(scoringTx).finishSession(eq(SESSION_ID), eq(ATTEMPT), eq(70), any());
    }

    @Test
    @DisplayName("미리 채점 중인 카드는 다시 채점하지 않고 기다렸다가 완료 결과로 총점을 낸다")
    void waitForInProgressCard() {
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(
                List.of(qaCard(FeedbackStatus.SCORING, true, null)),
                List.of(qaCard(FeedbackStatus.COMPLETED, false, 90)));
        givenSummaryAndSave(true);

        service.score(SESSION_ID, ATTEMPT);

        verify(scoringTx, times(2)).loadCards(SESSION_ID);
        verify(cardScoringService, never()).scoreTopic(anyLong());
        verify(scoringTx).finishSession(eq(SESSION_ID), eq(ATTEMPT), eq(90), any());
    }

    @Test
    @DisplayName("대기 시간 초과로 실패한 세션은 남은 카드가 모두 끝나면 새 시도로 마무리된다")
    void resumeAfterTimeout() {
        given(scoringTx.reopenIfAllCardsDone(SESSION_ID)).willReturn(Optional.of(ATTEMPT));
        given(scoringTx.loadPlan(SESSION_ID)).willReturn(qaPlan());
        given(scoringTx.loadCards(SESSION_ID)).willReturn(List.of(qaCard(FeedbackStatus.COMPLETED, false, 85)));
        givenSummaryAndSave(true);

        service.resumeIfReady(SESSION_ID);

        verify(cardScoringService, never()).scoreTopic(anyLong());
        verify(scoringTx).finishSession(eq(SESSION_ID), eq(ATTEMPT), eq(85), any());
    }

    @Test
    @DisplayName("아직 남은 카드가 있거나 실패한 세션이 아니면 재개하지 않는다")
    void noResumeWhenNotReady() {
        given(scoringTx.reopenIfAllCardsDone(SESSION_ID)).willReturn(Optional.empty());

        service.resumeIfReady(SESSION_ID);

        verify(scoringTx, never()).loadPlan(anyLong());
    }
}
