package com.benly.pt.dto;

public record PtTaskResponse(
        Long taskId,
        int version,
        Long companyId,
        String companyName,
        PtTaskContent content
) {
}