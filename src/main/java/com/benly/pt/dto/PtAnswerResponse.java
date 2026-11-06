package com.benly.pt.dto;

import com.benly.question.entity.AnswerType;

public record PtAnswerResponse(
        Long answerId,
        Long questionId,
        AnswerType inputType,
        String transcript,
        Integer durationSec,
        PtInterviewProgressResponse progress) {}