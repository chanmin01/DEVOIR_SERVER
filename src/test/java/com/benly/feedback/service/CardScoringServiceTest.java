package com.benly.feedback.service;

import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.service.CardScoringTx.TopicPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CardScoringServiceTest {

    @Mock private CardScoringTx tx;
    @Mock private CardScorer scorer;

    @InjectMocks
    private CardScoringService service;

    private static final Long MAIN_ID = 100L;
    private static final Long FEEDBACK_ID = 7L;

    private TopicPlan plan() {
        return new TopicPlan(FEEDBACK_ID, MAIN_ID,
                new ScoringContext("IT", "PERSONALITY", "백엔드"),
                AxisSet.PERSONALITY,
                new MainQuestionInput(1, "메인", "답변", List.of()));
    }

    @Test
    @DisplayName("이미 끝났거나 다른 곳에서 채점 중인 카드는 건너뛴다")
    void skipWhenNotClaimed() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.empty());

        boolean scored = service.scoreTopic(MAIN_ID);

        assertThat(scored).isFalse();
        verify(scorer, never()).scoreTopic(any(), any(), any(), any());
    }

    @Test
    @DisplayName("두 곳에서 동시에 처음 잡으면 늦은 쪽은 건너뛴다")
    void skipOnConcurrentClaim() {
        given(tx.claimTopic(MAIN_ID)).willThrow(new DataIntegrityViolationException("dup"));

        boolean scored = service.scoreTopic(MAIN_ID);

        assertThat(scored).isFalse();
        verify(scorer, never()).scoreTopic(any(), any(), any(), any());
    }

    @Test
    @DisplayName("채점에 성공하면 카드를 완료로 저장한다")
    void completeOnSuccess() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        ScoredTopic scoredTopic = new ScoredTopic(MAIN_ID, new MainQuestionScore("제목", Map.of(), null), 80, 0);
        given(scorer.scoreTopic(any(), any(), any(), any())).willReturn(scoredTopic);

        boolean scored = service.scoreTopic(MAIN_ID);

        assertThat(scored).isTrue();
        verify(tx).completeTopic(FEEDBACK_ID, scoredTopic);
        verify(tx, never()).failCard(anyLong());
    }

    @Test
    @DisplayName("채점이 실패하면 예외를 밖으로 던지지 않고 카드를 실패로 기록한다")
    void failCardOnError() {
        given(tx.claimTopic(MAIN_ID)).willReturn(Optional.of(plan()));
        given(scorer.scoreTopic(any(), any(), any(), any())).willThrow(new RuntimeException("Claude 오류"));

        boolean scored = service.scoreTopic(MAIN_ID);

        assertThat(scored).isFalse();
        verify(tx).failCard(FEEDBACK_ID);
        verify(tx, never()).completeTopic(anyLong(), any());
    }
}
