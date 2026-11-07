package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackCommandService {

    private final FeedbackScoringClient scoringClient;
    private final FeedbackScoringTx scoringTx;

    @Async
    public void score(Long sessionId, LocalDateTime attempt) {
        try {
            long startedAt = System.currentTimeMillis();

            ScoringPlan plan = scoringTx.loadPlan(sessionId);
            List<ScoredTopic> topics = scoreTopicsInParallel(plan);
            long topicsDoneAt = System.currentTimeMillis();

            // 발표 카드 채점은 PT 채점 이슈에서 붙인다. 지금은 발표 점수 없이 계산
            int totalScore = TotalScoreCalculator.calculate(
                    plan.format(), null,
                    topics.stream().map(ScoredTopic::topicScore).toList()).total();
            int totalSkipped = topics.stream().mapToInt(ScoredTopic::skippedCount).sum();

            SessionSummary summary = scoringClient.summarize(
                    plan.ctx(), totalScore,
                    topics.stream().map(ScoredTopic::score).toList(), totalSkipped);
            long summaryDoneAt = System.currentTimeMillis();

            boolean saved = scoringTx.saveResults(sessionId, attempt, totalScore, topics, summary);
            if (!saved) {
                log.warn("새 채점이 이미 시작돼서 이전 결과는 버림 sessionId={}", sessionId);
                return;
            }

            log.info("채점 단계 sessionId={} 형식={} 주제수={} 주제채점={}ms 총평={}ms 전체={}ms",
                    sessionId,
                    plan.format(),
                    topics.size(),
                    topicsDoneAt - startedAt,
                    summaryDoneAt - topicsDoneAt,
                    System.currentTimeMillis() - startedAt);
            log.info("채점 완료 sessionId={}, totalScore={}", sessionId, totalScore);
        } catch (Exception e) {
            log.error("채점 실패 sessionId={}", sessionId, e);
            scoringTx.markFailed(sessionId, attempt);
        }
    }

    private List<ScoredTopic> scoreTopicsInParallel(ScoringPlan plan) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, plan.mains().size()));
        try {
            List<CompletableFuture<ScoredTopic>> futures = plan.mains().stream()
                    .map(mi -> CompletableFuture.supplyAsync(() -> scoreOne(plan, mi), pool))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            pool.shutdown();
        }
    }

    private ScoredTopic scoreOne(ScoringPlan plan, ScoringPlan.MainInput mi) {
        MainQuestionInput in = mi.input();

        MainQuestionScore score = in.isFullySkipped()
                ? MainQuestionScore.skipped(plan.axes(), in.question())
                : scoringClient.scoreMainQuestion(plan.axes(), plan.ctx(), in).scaledBy(in.completeness());

        int topicScore = plan.axisSet().weightedScore(score.axisScores());
        return new ScoredTopic(mi.mainQuestionId(), score, topicScore, in.skippedCount());
    }
}
