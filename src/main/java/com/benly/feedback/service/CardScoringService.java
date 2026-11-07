package com.benly.feedback.service;

import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.service.CardScoringTx.PresentationPlan;
import com.benly.feedback.service.CardScoringTx.TopicPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 카드 한 장을 잡고, 채점하고, 저장한다.
 * 실패해도 예외를 밖으로 던지지 않는다. 면접 진행이나 다른 카드에 영향을 주지 않기 위해서다.
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
        }
        if (claimed.isEmpty()) {
            return false;
        }

        TopicPlan plan = claimed.get();
        long startedAt = System.currentTimeMillis();
        try {
            ScoredTopic scored = scorer.scoreTopic(
                    plan.mainQuestionId(), plan.axisSet(), plan.ctx(), plan.input());
            tx.completeTopic(plan.feedbackId(), scored);
            log.info("질문 카드 채점 완료 mainQuestionId={} score={} {}ms",
                    mainQuestionId, scored.topicScore(), System.currentTimeMillis() - startedAt);
            return true;
        } catch (Exception e) {
            log.error("질문 카드 채점 실패 mainQuestionId={}", mainQuestionId, e);
            tx.failCard(plan.feedbackId());
            return false;
        }
    }

    /** 발표 카드 채점. 반환 의미는 scoreTopic과 같다 */
    public boolean scorePresentation(Long sessionId) {
        Optional<PresentationPlan> claimed = tx.claimPresentation(sessionId);
        if (claimed.isEmpty()) {
            return false;
        }

        PresentationPlan plan = claimed.get();
        long startedAt = System.currentTimeMillis();
        try {
            ScoredPresentation scored = scorer.scorePresentation(plan.ctx(), plan.input());
            tx.completePresentation(plan.feedbackId(), scored);
            log.info("발표 카드 채점 완료 sessionId={} score={} {}ms",
                    sessionId, scored.topicScore(), System.currentTimeMillis() - startedAt);
            return true;
        } catch (Exception e) {
            log.error("발표 카드 채점 실패 sessionId={}", sessionId, e);
            tx.failCard(plan.feedbackId());
            return false;
        }
    }
}
