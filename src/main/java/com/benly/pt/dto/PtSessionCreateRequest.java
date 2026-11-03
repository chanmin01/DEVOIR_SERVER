package com.benly.pt.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record PtSessionCreateRequest(
        @NotNull(message = "요청 ID는 필수입니다.")
        UUID requestId,

        @NotNull(message = "기업 ID는 필수입니다.")
        @Positive
        Long companyId,

        @NotNull(message = "준비 시간은 필수입니다.")
        @Min(15)
        @Max(20)
        Integer preparationMinutes,

        @Size(max = 100)
        String jobRole
) {
}