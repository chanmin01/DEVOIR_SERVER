package com.benly.feedback.service;

import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.service.CardScoringTx.PresentationPlan;
import com.benly.feedback.service.CardScoringTx.TopicPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 카드 한 장을 잡고, 채점하고, 저장한다.
 * 잡기에서 생긴 일시적 DB 오류와 채점 오류는 false로 돌려 다음 재시도로 넘긴다.
 * 대상이 없는 것 같은 업무 오류는 숨기지 않고 그대로 던진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardScoringService {

    private final CardScoringTx tx;
    private final CardScorer scorer;

    /** 이 호출에서 채점을 끝냈으면 true. 이미 끝났거나 다른 곳에서 채점 중이거나 실패하면 false */
    public boolean scoreTopic(Long mainQuestionId) {
        Optional<TopicPlan> claimed;
        try {
            claimed = tx.claimTopic(mainQuestionId);
        } catch (DataIntegrityViolationException e) {
            log.info("질문 카드를 다른 곳에서 먼저 잡음 mainQuestionId={}", mainQuestionId);
            return false;
        } catch (TransientDataAccessException e) {
            log.warn("질문 카드 잡기 일시 실패 mainQuestionId={}", mainQuestionId, e);
            return false;
        }
        if (claimed.isEmpty()) {
            return false;
        }

        TopicPlan plan = claimed.get();
        long startedAt = System.currentTimeMillis();
        try {
            ScoredTopic scored = scorer.scoreTopic(
                    plan.mainQuestionId(), plan.axisSet(), plan.ctx(), plan.input());
            if (!tx.completeTopic(plan.feedbackId(), plan.attempt(), scored)) {
                log.info("질문 카드를 다른 시도가 가져가 결과를 버림 mainQuestionId={}", mainQuestionId);
                return false;
            }
            log.info("질문 카드 채점 완료 mainQuestionId={} score={} {}ms",
                    mainQuestionId, scored.topicScore(), System.currentTimeMillis() - startedAt);
            return true;
        } catch (Exception e) {
            log.error("질문 카드 채점 실패 mainQuestionId={}", mainQuestionId, e);
            failQuietly(plan.feedbackId(), plan.attempt());
            return false;
        }
    }

    /** 발표 카드 채점. 반환 의미는 scoreTopic과 같다 */
    public boolean scorePresentation(Long sessionId) {
        Optional<PresentationPlan> claimed;
        try {
            claimed = tx.claimPresentation(sessionId);
        } catch (TransientDataAccessException e) {
            log.warn("발표 카드 잡기 일시 실패 sessionId={}", sessionId, e);
            return false;
        }
        if (claimed.isEmpty()) {
            return false;
        }

        PresentationPlan plan = claimed.get();
        long startedAt = System.currentTimeMillis();
        try {
            ScoredPresentation scored = scorer.scorePresentation(plan.ctx(), plan.input());
            if (!tx.completePresentation(plan.feedbackId(), plan.attempt(), scored)) {
                log.info("발표 카드를 다른 시도가 가져가 결과를 버림 sessionId={}", sessionId);
                return false;
            }
            log.info("발표 카드 채점 완료 sessionId={} score={} {}ms",
                    sessionId, scored.topicScore(), System.currentTimeMillis() - startedAt);
            return true;
        } catch (Exception e) {
            log.error("발표 카드 채점 실패 sessionId={}", sessionId, e);
            failQuietly(plan.feedbackId(), plan.attempt());
            return false;
        }
    }

    /** 실패 기록 자체가 실패해도 던지지 않는다. 카드는 10분 뒤 멈춘 카드로 다시 채점된다 */
    private void failQuietly(Long feedbackId, LocalDateTime attempt) {
        try {
            tx.failCard(feedbackId, attempt);
        } catch (Exception e) {
            log.warn("카드 실패 기록 실패 feedbackId={}", feedbackId, e);
        }
    }
}
