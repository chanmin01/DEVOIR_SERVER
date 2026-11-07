package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringPlan;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.PresentationScore;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.entity.AxisSet;
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

            List<ScoredTopic> topics;
            ScoredPresentation presentation;

            // 발표와 질문 채점을 한 번에 병렬로 돌린다
            int threads = Math.max(1, plan.mains().size() + (plan.presentation() != null ? 1 : 0));
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CompletableFuture<ScoredPresentation> presentationFuture = plan.presentation() == null
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.supplyAsync(() -> scorePresentation(plan), pool);

                List<CompletableFuture<ScoredTopic>> topicFutures = plan.mains().stream()
                        .map(mi -> CompletableFuture.supplyAsync(() -> scoreOne(plan, mi), pool))
                        .toList();

                topics = topicFutures.stream().map(CompletableFuture::join).toList();
                presentation = presentationFuture.join();
            } finally {
                pool.shutdown();
            }
            long topicsDoneAt = System.currentTimeMillis();

            int totalScore = TotalScoreCalculator.calculate(
                    plan.format(),
                    presentation == null ? null : presentation.topicScore(),
                    topics.stream().map(ScoredTopic::topicScore).toList()).total();
            int totalSkipped = topics.stream().mapToInt(ScoredTopic::skippedCount).sum();

            SessionSummary summary = scoringClient.summarize(
                    plan.ctx(), totalScore,
                    topics.stream().map(ScoredTopic::score).toList(),
                    presentation == null ? null : presentation.score(),
                    totalSkipped);
            long summaryDoneAt = System.currentTimeMillis();

            boolean saved = scoringTx.saveResults(sessionId, attempt, totalScore, topics, presentation, summary);
            if (!saved) {
                log.warn("새 채점이 이미 시작돼서 이전 결과는 버림 sessionId={}", sessionId);
                return;
            }

            log.info("채점 단계 sessionId={} 형식={} 주제수={} 발표={} 주제채점={}ms 총평={}ms 전체={}ms",
                    sessionId,
                    plan.format(),
                    topics.size(),
                    presentation == null ? "없음" : presentation.topicScore(),
                    topicsDoneAt - startedAt,
                    summaryDoneAt - topicsDoneAt,
                    System.currentTimeMillis() - startedAt);
            log.info("채점 완료 sessionId={}, totalScore={}", sessionId, totalScore);
        } catch (Exception e) {
            log.error("채점 실패 sessionId={}", sessionId, e);
            scoringTx.markFailed(sessionId, attempt);
        }
    }

    /** 발표가 기록되지 않았으면 AI를 부르지 않고 0점 처리 */
    private ScoredPresentation scorePresentation(ScoringPlan plan) {
        PresentationInput in = plan.presentation();
        AxisSet axisSet = AxisSet.PT;

        PresentationScore score = in.isMissing()
                ? PresentationScore.missing(axisSet.axes(), in.requirements())
                : scoringClient.scorePresentation(axisSet.axes(), plan.ctx(), in);

        return new ScoredPresentation(score, axisSet.weightedScore(score.axisScores()));
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
