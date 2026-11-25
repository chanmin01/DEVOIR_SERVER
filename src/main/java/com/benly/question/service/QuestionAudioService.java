package com.benly.question.service;

import com.benly.question.client.GeminiTtsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionAudioService {

    private final QuestionAudioQueryService questionAudioQueryService;
    private final GeminiTtsClient geminiTtsClient;

    /**
     * 질문 문장을 음성(MP3)으로 만든다.
     * 권한 확인은 짧은 읽기 트랜잭션에서 끝내고, 외부 TTS 호출은 트랜잭션 밖에서 해 DB 커넥션을 잡고 있지 않는다.
     */
    public byte[] createQuestionAudio(Long questionId, Long userId) {
        String content = questionAudioQueryService.findOwnedQuestionContent(questionId, userId);
        return geminiTtsClient.synthesize(content);
    }
}
