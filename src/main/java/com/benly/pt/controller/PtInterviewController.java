package com.benly.pt.controller;

import com.benly.global.common.ApiResponse;
import com.benly.pt.dto.*;
import com.benly.pt.service.*;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pt/sessions/{sessionId}")
public class PtInterviewController {
    private final PtInterviewService interview;
    private final PtInterviewTx tx;
    private final PtAudioService audio;

    @PostMapping("/questions/next")
    public ApiResponse<PtInterviewProgressResponse> next(
            @AuthenticationPrincipal Long userId, @PathVariable Long sessionId) {
        return ApiResponse.success("PT 질문을 제공합니다.", interview.next(userId, sessionId));
    }

    @GetMapping("/questions/current")
    public ApiResponse<PtInterviewProgressResponse> current(
            @AuthenticationPrincipal Long userId, @PathVariable Long sessionId) {
        return ApiResponse.success("PT 진행 상태를 조회했습니다.", tx.current(userId, sessionId));
    }

    @PostMapping("/questions/{questionId}/answers")
    public ApiResponse<PtAnswerResponse> text(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @Valid @RequestBody PtAnswerRequest request) {
        return ApiResponse.success(
                "PT 답변이 저장되었습니다.", tx.submitText(userId, sessionId, questionId, request));
    }

    @PostMapping(
            value = "/questions/{questionId}/answers/audio",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PtAnswerResponse> audioAnswer(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @RequestParam UUID requestId,
            @RequestParam MultipartFile audio,
            @RequestParam Integer durationSec) {
        return ApiResponse.success(
                "PT 음성 답변이 저장되었습니다.",
                this.audio.answer(userId, sessionId, questionId, requestId, audio, durationSec));
    }

    @PostMapping(value = "/presentation/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PtProgressResponse> audioPresentation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sessionId,
            @RequestParam UUID requestId,
            @RequestParam MultipartFile audio,
            @RequestParam Integer durationSec) {
        return ApiResponse.success(
                "PT 음성 발표가 저장되었습니다.",
                this.audio.presentation(userId, sessionId, requestId, audio, durationSec));
    }

    @GetMapping("/evaluation-input")
    public ApiResponse<PtEvaluationInputResponse> evaluationInput(
            @AuthenticationPrincipal Long userId, @PathVariable Long sessionId) {
        return ApiResponse.success("PT 평가 입력 데이터를 조회했습니다.", tx.evaluationInput(userId, sessionId));
    }
}