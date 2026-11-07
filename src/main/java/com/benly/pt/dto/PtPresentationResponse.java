package com.benly.pt.dto;

import com.benly.question.entity.AnswerType;

import java.time.Instant;

public record PtPresentationResponse(
        String transcript,
        AnswerType inputType,
        Instant submittedAt
) {
}