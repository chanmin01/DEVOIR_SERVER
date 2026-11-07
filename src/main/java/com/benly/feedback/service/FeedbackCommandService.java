package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.CardSnapshot;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationScore;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 최종 채점. 면접 중 미리 채점된 카드는 건너뛰고, 빠지거나 실패한 카드만 채점한 뒤
 * 모든 카드가 끝나면 총점과 총평을 만들어 세션을 완료로 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackCommandService {

    /** 미리 채점이 진행 중인 카드를 기다리는 최대 시간 */
    static final long WAIT_LIMIT_MILLIS = 120_000;
    static final long POLL_MILLIS = 2_000;
    /** 같은 카드를 이 횟수보다 많이 채점해야 하면 세션 채점을 실패로 끝낸다 */
    static final int MAX_TRIES_PER_CARD = 2;

    private static final String PRESENTATION_KEY = "presentation";

    private final FeedbackScoringClient scoringClient;
    private final FeedbackScoringTx scoringTx;
    private final CardScoringService cardScoringService;

    @Async
    public void score(Long sessionId, LocalDateTime attempt) {
        try {
            long startedAt = System.currentTimeMillis();

            ScoringPlan plan = scoringTx.loadPlan(sessionId);
            List<CardSnapshot> cards = fillCards(sessionId, plan);
            long cardsDoneAt = System.currentTimeMillis();

            List<CardSnapshot> qaCards = plan.mains().stream()
                    .map(mi -> findQa(cards, mi.mainQuestionId()))
                    .toList();
            CardSnapshot presentationCard = plan.presentation() == null ? null : findPresentation(cards);

            int totalScore = TotalScoreCalculator.calculate(
                    plan.format(),
                    presentationCard == null ? null : presentationCard.topicScore(),
                    qaCards.stream().map(c -> nvl(c.topicScore())).toList()).total();
            int totalSkipped = plan.mains().stream().mapToInt(mi -> mi.input().skippedCount()).sum();

            SessionSummary summary = scoringClient.summarize(
                    plan.ctx(),
                    totalScore,
                    qaCards.stream().map(c -> new MainQuestionScore(c.shortTitle(), Map.of(), c.content())).toList(),
                    presentationCard == null ? null : new PresentationScore(Map.of(), presentationCard.content()),
                    totalSkipped);

            boolean saved = scoringTx.finishSession(sessionId, attempt, totalScore, summary);
            if (!saved) {
                log.warn("새 채점이 이미 시작돼서 이전 결과는 버림 sessionId={}", sessionId);
                return;
            }

            log.info("채점 완료 sessionId={} 형식={} totalScore={} 카드채우기={}ms 총평={}ms",
                    sessionId, plan.format(), totalScore,
                    cardsDoneAt - startedAt, System.currentTimeMillis() - cardsDoneAt);
        } catch (Exception e) {
            log.error("채점 실패 sessionId={}", sessionId, e);
            scoringTx.markFailed(sessionId, attempt);
        }
    }

    /**
     * 미리 채점 카드가 늦게 끝나 모든 카드가 모였으면, 실패했던 세션 채점을 이어서 마무리한다.
     * 리스너의 비동기 스레드에서 불리므로 score를 같은 스레드에서 바로 실행한다.
     * 새 시도 번호로 돌기 때문에 사용자가 동시에 재채점을 요청해도 finishSession에서 한쪽만 저장된다.
     */
    public void resumeIfReady(Long sessionId) {
        scoringTx.reopenIfAllCardsDone(sessionId).ifPresent(attempt -> {
            log.info("늦게 끝난 카드로 세션 채점 재개 sessionId={}", sessionId);
            score(sessionId, attempt);
        });
    }

    /**
     * 모든 카드가 COMPLETED가 될 때까지 빠진 카드는 채점하고, 다른 곳에서 채점 중인 카드는 기다린다.
     */
    List<CardSnapshot> fillCards(Long sessionId, ScoringPlan plan) throws InterruptedException {
        Map<String, Integer> tries = new HashMap<>();
        long deadline = System.currentTimeMillis() + WAIT_LIMIT_MILLIS;

        while (true) {
            List<CardSnapshot> cards = scoringTx.loadCards(sessionId);
            List<Runnable> work = new ArrayList<>();
            boolean waiting = false;

            for (ScoringPlan.MainInput mi : plan.mains()) {
                CardSnapshot card = findQa(cards, mi.mainQuestionId());
                if (card != null && card.completed()) continue;
                if (card != null && card.inProgress()) { waiting = true; continue; }

                countTry(tries, "q" + mi.mainQuestionId());
                work.add(() -> cardScoringService.scoreTopic(mi.mainQuestionId()));
            }

            if (plan.presentation() != null) {
                CardSnapshot card = findPresentation(cards);
                if (card != null && card.inProgress()) {
                    waiting = true;
                } else if (card == null || !card.completed()) {
                    countTry(tries, PRESENTATION_KEY);
                    work.add(() -> cardScoringService.scorePresentation(sessionId));
                }
            }

            if (work.isEmpty() && !waiting) {
                return cards;
            }
            if (!work.isEmpty()) {
                runInParallel(work);
                continue;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("미리 채점 중인 카드가 끝나지 않음 sessionId=" + sessionId);
            }
            Thread.sleep(POLL_MILLIS);
        }
    }

    private void countTry(Map<String, Integer> tries, String key) {
        int count = tries.merge(key, 1, Integer::sum);
        if (count > MAX_TRIES_PER_CARD) {
            throw new IllegalStateException("카드 채점이 반복해서 실패함 card=" + key);
        }
    }

    private void runInParallel(List<Runnable> work) {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        try {
            List<CompletableFuture<Void>> futures = work.stream()
                    .map(w -> CompletableFuture.runAsync(w, pool))
                    .toList();
            futures.forEach(CompletableFuture::join);
        } finally {
            pool.shutdown();
        }
    }

    private CardSnapshot findQa(List<CardSnapshot> cards, Long questionId) {
        return cards.stream()
                .filter(c -> !c.presentation() && Objects.equals(c.questionId(), questionId))
                .findFirst().orElse(null);
    }

    private CardSnapshot findPresentation(List<CardSnapshot> cards) {
        return cards.stream().filter(CardSnapshot::presentation).findFirst().orElse(null);
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
