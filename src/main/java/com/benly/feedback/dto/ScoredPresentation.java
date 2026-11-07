package com.benly.feedback.dto;

import com.benly.feedback.dto.ScoringResult.PresentationScore;

public record ScoredPresentation(
        PresentationScore score,
        int topicScore
) {}
