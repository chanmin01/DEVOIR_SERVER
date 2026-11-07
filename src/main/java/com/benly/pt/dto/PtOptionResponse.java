package com.benly.pt.dto;

import java.util.List;

public record PtOptionResponse(
        List<PtCompanyOptionResponse> companies,
        List<TrackOption> tracks,
        List<Integer> preparationMinutes,
        int presentationSeconds,
        int mainQuestionCount,
        int followUpQuestionCountPerMain,
        int totalQuestionCount
) {
    public record TrackOption(
            String code,
            String label
    ) {
    }
}