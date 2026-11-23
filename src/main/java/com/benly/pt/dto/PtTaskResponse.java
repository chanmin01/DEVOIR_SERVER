package com.benly.pt.dto;

public record PtTaskResponse(
        Long taskId,
        int version,
        Long companyId,
        String companyName,
        PtTaskContent content,
        PtTaskSettingsResponse settings
) {

    public PtTaskResponse(
            Long taskId,
            int version,
            Long companyId,
            String companyName,
            PtTaskContent content
    ) {
        this(
                taskId,
                version,
                companyId,
                companyName,
                content,
                PtTaskSettingsResponse.legacy()
        );
    }
}