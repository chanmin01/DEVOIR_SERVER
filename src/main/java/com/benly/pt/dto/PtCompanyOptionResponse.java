package com.benly.pt.dto;

import com.benly.pt.entity.PtTask;

public record PtCompanyOptionResponse(
        Long companyId,
        String companyName,
        PtTaskSettingsResponse settings
) {

    public PtCompanyOptionResponse(
            Long companyId,
            String companyName
    ) {
        this(
                companyId,
                companyName,
                PtTaskSettingsResponse.legacy()
        );
    }

    public PtCompanyOptionResponse(
            Long companyId,
            String companyName,
            PtTask task
    ) {
        this(
                companyId,
                companyName,
                PtTaskSettingsResponse.from(task)
        );
    }
}