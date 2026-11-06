package com.benly.pt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PtTextPresentationRequest(
        @NotNull(message = "요청 ID는 필수입니다.")
        UUID requestId,

        @NotBlank(message = "발표 내용은 필수입니다.")
        @Size(max = 30000, message = "발표는 30000자 이하여야 합니다.")
        String transcript
) {
}