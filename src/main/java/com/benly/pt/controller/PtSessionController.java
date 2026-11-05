package com.benly.pt.controller;

import com.benly.global.common.ApiResponse;
import com.benly.pt.dto.*;
import com.benly.pt.service.PtProgressService;
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
    private final PtProgressService progressService;

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

    @PutMapping("/{sessionId}/track")
    public ApiResponse<PtProgressResponse> selectTrack(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody PtTrackRequest request
    ) {
        return ApiResponse.success(
                "발표 분야를 선택했습니다.",
                progressService.selectTrack(
                        userId,
                        sessionId,
                        request
                )
        );
    }

    @PostMapping("/{sessionId}/presentation/start")
    public ApiResponse<PtProgressResponse> startPresentation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "발표를 시작합니다.",
                progressService.startPresentation(
                        userId,
                        sessionId
                )
        );
    }

    @PostMapping("/{sessionId}/presentation/text")
    public ApiResponse<PtProgressResponse> submitText(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @Valid @RequestBody PtTextPresentationRequest request
    ) {
        return ApiResponse.success(
                "발표가 저장되었습니다.",
                progressService.submitText(
                        userId,
                        sessionId,
                        request
                )
        );
    }

    @PostMapping("/{sessionId}/cancel")
    public ApiResponse<PtProgressResponse> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(
                "PT 면접을 취소했습니다.",
                progressService.cancel(
                        userId,
                        sessionId
                )
        );
    }
}