package com.benly.pt.service;

import com.benly.pt.dto.PtInterviewProgressResponse;

import java.util.UUID;

public record PtGenerationPlan(
        UUID operationToken,
        int ordinal,
        int mainNumber,
        int followUpNumber,
        String context,
        PtInterviewProgressResponse existing) {}