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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackCommandService {

    private final FeedbackScoringClient scoringClient;
    private final FeedbackScoringTx scoringTx;   // DB는 이쪽에 위임

    @Async
    public void score(Long sessionId) {
        try {
            long startedAt = System.currentTimeMillis();

            ScoringPlan plan = scoringTx.loadPlan(sessionId);
            List<ScoredTopic> topics = scoreTopicsInParallel(plan);
            long topicsDoneAt = System.currentTimeMillis();

            int totalScore = averageTopicScore(topics);
            int totalSkipped = topics.stream().mapToInt(ScoredTopic::skippedCount).sum();

            SessionSummary summary = scoringClient.summarize(
                    plan.ctx(), totalScore,
                    topics.stream().map(ScoredTopic::score).toList(), totalSkipped);
            long summaryDoneAt = System.currentTimeMillis();

            scoringTx.saveResults(sessionId, totalScore, topics, summary);

            log.info("채점 단계 sessionId={} 주제수={} 주제채점={}ms 총평={}ms 전체={}ms",
                    sessionId,
                    topics.size(),
                    topicsDoneAt - startedAt,
                    summaryDoneAt - topicsDoneAt,
                    System.currentTimeMillis() - startedAt);
            log.info("채점 완료 sessionId={}, totalScore={}", sessionId, totalScore);
        } catch (Exception e) {
            log.error("채점 실패 sessionId={}", sessionId, e);
            scoringTx.markFailed(sessionId);
        }
    }

    /**
     * 메인 질문들을 동시에 채점한다 (서로 독립적).
     */
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

    /**
     * 한 주제 채점: 판단은 입력/점수 객체에게 위임한다.
     */
    private ScoredTopic scoreOne(ScoringPlan plan, ScoringPlan.MainInput mi) {
        MainQuestionInput in = mi.input();

        MainQuestionScore score = in.isFullySkipped()
                ? MainQuestionScore.skipped(plan.axes(), in.question())
                : scoringClient.scoreMainQuestion(plan.axes(), plan.ctx(), in).scaledBy(in.completeness());

        int topicScore = plan.axisSet().weightedScore(score.axisScores());
        return new ScoredTopic(mi.mainQuestionId(), score, topicScore, in.skippedCount());
    }

    private int averageTopicScore(List<ScoredTopic> topics) {
        if (topics.isEmpty()) return 0;
        int sum = topics.stream().mapToInt(ScoredTopic::topicScore).sum();
        return Math.round((float) sum / topics.size());
    }
}