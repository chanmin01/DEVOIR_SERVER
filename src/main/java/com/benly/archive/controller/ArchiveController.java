package com.benly.archive.controller;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.dto.ArchiveCreateResponse;
import com.benly.archive.service.ArchiveRegisterService;
import com.benly.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveRegisterService archiveRegisterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArchiveCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ArchiveCreateRequest request) {
        return ApiResponse.created("질문을 등록했어요.", archiveRegisterService.register(userId, request));
    }
}