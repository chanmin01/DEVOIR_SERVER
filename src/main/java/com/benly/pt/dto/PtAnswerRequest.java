package com.benly.pt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PtAnswerRequest(
        @NotNull UUID requestId, @NotBlank @Size(max = 30000) String transcript) {}