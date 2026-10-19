package com.benly.question.dto;

import com.benly.question.entity.FollowUpStrategy;

public record FollowUpResult(
        String question,
        FollowUpStrategy strategy
) {
}