package com.benly.feedback.service;

import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.service.CardScoringTx.TopicPlan;
import com.benly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardScoringServiceTest {

    @Mock
    private CardScoringTx tx;
    @Mock
    private CardScorer scorer;

    @InjectMocks
    private CardScoringService service;

    private static final Long MAIN_ID = 100L;
    private static final Long SESSION_ID = 8L;
    private static final Long FEEDBACK_ID = 7L;
    private static final LocalDateTime ATTEMPT = LocalDateTime.of(2026, 11, 7, 12, 0);

    private TopicPlan plan() {
        return new TopicPlan(FEEDBACK_ID, ATTEMPT, MAIN_ID,
                new ScoringContext("IT", "PERSONALITY", "백엔드"),
                AxisSet.PERSONALITY,
                new MainQuestionInput(1, "메인", "답변", List.of()));
    }

    private ScoredTopic scoredTopic() {
        return new ScoredTopic(MAIN_ID, new MainQuestionScore("제목", Map.of(), null), 80, 0);
    }

    @Test
    @DisplayName("이미 끝났거나 다른 곳에서 채점 중인 카드는 건너뛴다")
    void skipWhenNotClaimed() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.empty());

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
        verify(scorer, never()).scoreTopic(any(), any(), any(), any());
    }

    @Test
    @DisplayName("두 곳에서 동시에 처음 잡으면 늦은 쪽은 건너뛴다")
    void skipOnConcurrentClaim() {
        given(tx.claimTopic(MAIN_ID)).willThrow(new DataIntegrityViolationException("dup"));

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
        verify(scorer, never()).scoreTopic(any(), any(), any(), any());
    }

    @Test
    @DisplayName("질문 카드 잡기에서 잠금 획득이 일시적으로 실패하면 false로 돌려 다음 재시도로 넘긴다")
    void topicClaimTransientFailure() {
        given(tx.claimTopic(MAIN_ID)).willThrow(new CannotAcquireLockException("lock timeout"));

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
        verify(scorer, never()).scoreTopic(any(), any(), any(), any());
    }

    @Test
    @DisplayName("발표 카드 잡기에서 잠금 획득이 일시적으로 실패하면 false로 돌려 다음 재시도로 넘긴다")
    void presentationClaimTransientFailure() {
        given(tx.claimPresentation(SESSION_ID)).willThrow(new CannotAcquireLockException("lock timeout"));

        assertThat(service.scorePresentation(SESSION_ID)).isFalse();
        verify(scorer, never()).scorePresentation(any(), any());
    }

    @Test
    @DisplayName("대상이 없는 업무 오류는 숨기지 않고 그대로 던진다")
    void businessErrorPropagates() {
        given(tx.claimTopic(MAIN_ID))
                .willThrow(new BusinessException(FeedbackErrorCode.QUESTION_NOT_FOUND));

        assertThatThrownBy(() -> service.scoreTopic(MAIN_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("채점에 성공하면 이 시도 번호로 카드를 완료로 저장한다")
    void completeOnSuccess() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        ScoredTopic scored = scoredTopic();
        given(scorer.scoreTopic(any(), any(), any(), any())).willReturn(scored);
        given(tx.completeTopic(FEEDBACK_ID, ATTEMPT, scored)).willReturn(true);

        assertThat(service.scoreTopic(MAIN_ID)).isTrue();
        verify(tx, never()).failCard(anyLong(), any());
    }

    @Test
    @DisplayName("다른 시도가 카드를 가져갔으면 결과를 버리고 false를 돌려준다")
    void discardWhenAttemptReplaced() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        ScoredTopic scored = scoredTopic();
        given(scorer.scoreTopic(any(), any(), any(), any())).willReturn(scored);
        given(tx.completeTopic(FEEDBACK_ID, ATTEMPT, scored)).willReturn(false);

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
        verify(tx, never()).failCard(anyLong(), any());
    }

    @Test
    @DisplayName("채점이 실패하면 예외를 던지지 않고 이 시도 번호로 카드를 실패로 기록한다")
    void failCardOnError() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        given(scorer.scoreTopic(any(), any(), any(), any())).willThrow(new RuntimeException("Claude 오류"));

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
        verify(tx).failCard(FEEDBACK_ID, ATTEMPT);
        verify(tx, never()).completeTopic(anyLong(), any(), any());
    }

    @Test
    @DisplayName("실패 기록 자체가 실패해도 예외를 밖으로 던지지 않는다")
    void failCardErrorSwallowed() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        given(scorer.scoreTopic(any(), any(), any(), any())).willThrow(new RuntimeException("Claude 오류"));
        org.mockito.BDDMockito.willThrow(new RuntimeException("DB 오류"))
                .given(tx).failCard(eq(FEEDBACK_ID), any());

        assertThat(service.scoreTopic(MAIN_ID)).isFalse();
    }
}
