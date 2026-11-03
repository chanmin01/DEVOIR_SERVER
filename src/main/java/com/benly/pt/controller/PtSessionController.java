package com.benly.pt.controller;

import com.benly.global.common.ApiResponse;
import com.benly.pt.dto.*;
import com.benly.pt.service.PtSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pt/sessions")
public class PtSessionController {

    private final PtSessionService service;

    @PostMapping
    public ApiResponse<PtSessionResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PtSessionCreateRequest request
    ) {
        return ApiResponse.success(
                "PT 과제가 공개되었습니다. 준비 시간이 시작됩니다.",
                service.create(userId, request)
        );
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<PtSessionResponse> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "PT 세션을 조회했습니다.",
                service.get(userId, sessionId)
        );
    }
}