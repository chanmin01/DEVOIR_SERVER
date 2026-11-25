package com.benly.question.controller;

import com.benly.global.common.ApiResponse;
import com.benly.question.dto.AnswerResponse;
import com.benly.question.service.AnswerService;
import com.benly.question.service.QuestionAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final AnswerService answerService;
    private final QuestionAudioService questionAudioService;

    @PostMapping("/{questionId}/skip")
    public ApiResponse<AnswerResponse> skipQuestion(
            @PathVariable Long questionId,
            @RequestParam Long sessionId,
            @AuthenticationPrincipal Long userId) {
        AnswerResponse data = answerService.skipQuestion(sessionId, userId, questionId);
        return ApiResponse.success("질문을 건너뛰었습니다.", data);
    }

    /**
     * 질문 음성(MP3). 바이너리라 ApiResponse로 감싸지 않는다. 실패하면 다른 API처럼 JSON 에러가 나간다.
     * produces를 audio/mpeg로 고정하지 않는 이유: 고정하면 실패 시 JSON 에러 응답을 쓰지 못해 406이 난다.
     * 같은 질문을 다시 요청하면 TTS 비용이 다시 들므로 브라우저가 잠깐 캐시하게 한다.
     */
    @GetMapping("/{questionId}/audio")
    public ResponseEntity<byte[]> getQuestionAudio(
            @PathVariable Long questionId,
            @AuthenticationPrincipal Long userId) {
        byte[] audio = questionAudioService.createQuestionAudio(questionId, userId);
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(audio);
    }
}
