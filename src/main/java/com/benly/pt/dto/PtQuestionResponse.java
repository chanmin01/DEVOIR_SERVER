package com.benly.pt.dto;

public record PtQuestionResponse(
        Long questionId,
        int ordinal,
        int mainNumber,
        int followUpNumber,
        String type,
        String content) {}