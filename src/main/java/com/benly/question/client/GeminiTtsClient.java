package com.benly.question.client;

import com.benly.global.exception.BusinessException;
import com.benly.question.exception.AnswerErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;

/**
 * 면접관 질문을 음성(MP3)으로 만든다. Google Cloud Text-to-Speech의 Gemini TTS를 쓴다.
 * 질문 음성은 한 번만 재생되므로 저장하지 않고 요청마다 생성한다.
 */
@Slf4j
@Component
public class GeminiTtsClient {

    /** 11/5 회의에서 Algieba로 들어보고 고른 면접관 말투. 문장 자체가 아니라 읽는 방식만 지시한다. */
    private static final String INTERVIEWER_STYLE =
            "Professional job interviewer at a Korean tech company. "
                    + "Natural standard Seoul Korean, polite formal speech. "
                    + "Calm, composed, slightly slower than conversation. "
                    + "Do not sound like an announcer or customer service.";

    private final RestClient restClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String voice;
    private final String languageCode;

    public GeminiTtsClient(
            @Value("${google.tts.api-key:}") String apiKey,
            @Value("${google.tts.api-url}") String apiUrl,
            @Value("${google.tts.model}") String model,
            @Value("${google.tts.voice}") String voice,
            @Value("${google.tts.language-code}") String languageCode
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        // 사용자가 질문 화면에서 기다리는 시간이라 길게 잡지 않는다. 넘기면 프론트가 텍스트로 진행한다.
        factory.setReadTimeout(20000);

        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.voice = voice;
        this.languageCode = languageCode;
    }

    public byte[] synthesize(String text) {
        // 키가 없는 환경(로컬, 키 등록 전 운영)에서도 앱은 떠야 하므로 시작 시점이 아니라 호출 시점에 막는다.
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google TTS API 키가 설정되지 않아 질문 음성을 만들 수 없습니다.");
            throw new BusinessException(AnswerErrorCode.TTS_FAILED);
        }

        SynthesizeRequest request = new SynthesizeRequest(
                new Input(INTERVIEWER_STYLE, text),
                new Voice(languageCode, voice, model),
                new AudioConfig("MP3")
        );

        SynthesizeResponse response;
        try {
            response = restClient.post()
                    .uri(apiUrl)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SynthesizeResponse.class);
        } catch (RestClientException e) {
            // 응답 본문에 키가 섞일 일은 없지만, 요청 원문은 남기지 않고 원인 메시지만 남긴다.
            log.warn("Google TTS 호출 실패: {}", e.getMessage());
            throw new BusinessException(AnswerErrorCode.TTS_FAILED);
        }

        if (response == null || response.audioContent() == null || response.audioContent().isBlank()) {
            log.warn("Google TTS 응답에 오디오가 없습니다.");
            throw new BusinessException(AnswerErrorCode.TTS_FAILED);
        }

        return Base64.getDecoder().decode(response.audioContent());
    }

    private record SynthesizeRequest(Input input, Voice voice, AudioConfig audioConfig) {
    }

    /** prompt는 말투 지시, text는 그대로 읽을 문장 */
    private record Input(String prompt, String text) {
    }

    private record Voice(String languageCode, String name, String modelName) {
    }

    private record AudioConfig(String audioEncoding) {
    }

    private record SynthesizeResponse(String audioContent) {
    }
}
