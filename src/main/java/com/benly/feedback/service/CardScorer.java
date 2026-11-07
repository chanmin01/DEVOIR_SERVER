package com.benly.feedback.service;

import com.benly.feedback.client.FeedbackScoringClient;
import com.benly.feedback.dto.ScoredPresentation;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.PresentationScore;
import com.benly.feedback.entity.AxisSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 카드 한 장을 Claude로 채점한다. DB에는 손대지 않는다.
 * 면접 중 미리 채점과 면접 후 일괄 채점이 같은 로직을 쓰도록 한곳에 모았다.
 */
@Component
@RequiredArgsConstructor
public class CardScorer {

    private final FeedbackScoringClient scoringClient;

    public ScoredTopic scoreTopic(Long mainQuestionId, AxisSet axisSet,
                                  ScoringContext ctx, MainQuestionInput in) {
        MainQuestionScore score = in.isFullySkipped()
                ? MainQuestionScore.skipped(axisSet.axes(), in.question())
                : scoringClient.scoreMainQuestion(axisSet.axes(), ctx, in).scaledBy(in.completeness());

        int topicScore = axisSet.weightedScore(score.axisScores());
        return new ScoredTopic(mainQuestionId, score, topicScore, in.skippedCount());
    }

    public ScoredPresentation scorePresentation(ScoringContext ctx, PresentationInput in) {
        AxisSet axisSet = AxisSet.PT;

        PresentationScore score = in.isMissing()
                ? PresentationScore.missing(axisSet.axes(), in.requirements())
                : scoringClient.scorePresentation(axisSet.axes(), ctx, in);

        return new ScoredPresentation(score, axisSet.weightedScore(score.axisScores()));
    }
}
