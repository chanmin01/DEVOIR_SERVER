package com.benly.question.service;

import com.benly.question.client.GeminiTtsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionAudioService {

    private final QuestionAudioQueryService questionAudioQueryService;
    private final GeminiTtsClient geminiTtsClient;

    public byte[] createQuestionAudio(Long questionId, Long userId) {
        String content = questionAudioQueryService.findOwnedQuestionContent(questionId, userId);
        return geminiTtsClient.synthesize(content);
    }
}
