package com.benly.question.client;

import com.benly.global.exception.BusinessException;
import com.benly.question.exception.AnswerErrorCode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * 면접관 질문을 음성(MP3)으로 만든다. Google Cloud Text-to-Speech의 Gemini TTS를 쓴다.
 * 질문 음성은 한 번만 재생되므로 저장하지 않고 요청마다 생성한다.
 *
 * 인증은 서비스 계정 키(JSON)로 한다. Gemini TTS는 aiplatform.endpoints.predict 권한을 확인하는데,
 * 일반 API 키에는 신원이 없어 403이 나고, 서비스 계정에 묶는 API 키는 TTS API와 함께 쓸 수 없다.
 */
@Slf4j
@Component
public class GeminiTtsClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    /** Algieba로 들어보고 고른 면접관 말투. 문장 자체가 아니라 읽는 방식만 지시한다. */
    private static final String INTERVIEWER_STYLE =
            "Professional job interviewer at a Korean tech company. "
                    + "Natural standard Seoul Korean, polite formal speech. "
                    + "Calm, composed, slightly slower than conversation. "
                    + "Do not sound like an announcer or customer service.";

    private final RestClient restClient;
    private final GoogleCredentials credentials;
    private final String projectId;
    private final String apiUrl;
    private final String model;
    private final String voice;
    private final String languageCode;

    public GeminiTtsClient(
            @Value("${google.tts.credentials:}") String credentialsBase64,
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
        this.apiUrl = apiUrl;
        this.model = model;
        this.voice = voice;
        this.languageCode = languageCode;

        ServiceAccountCredentials loaded = loadCredentials(credentialsBase64);
        this.credentials = loaded == null ? null : loaded.createScoped(List.of(SCOPE));
        this.projectId = loaded == null ? null : loaded.getProjectId();
    }

    /**
     * 키가 없거나 잘못돼도 앱은 떠야 하므로 시작 시점에 예외를 던지지 않는다. 호출 시점에 TTS_FAILED로 막는다.
     * 환경변수에 JSON을 그대로 넣으면 따옴표·줄바꿈 때문에 배포 스크립트에서 깨지기 쉬워 base64로 받는다.
     */
    private static ServiceAccountCredentials loadCredentials(String credentialsBase64) {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.warn("Google TTS 서비스 계정 키가 설정되지 않았습니다. 질문 음성은 생성되지 않습니다.");
            return null;
        }
        try {
            byte[] json = Base64.getMimeDecoder().decode(credentialsBase64.trim());
            return ServiceAccountCredentials.fromStream(new ByteArrayInputStream(json));
        } catch (IOException | RuntimeException e) {
            log.warn("Google TTS 서비스 계정 키를 읽지 못했습니다: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public byte[] synthesize(String text) {
        if (credentials == null) {
            log.warn("Google TTS 서비스 계정 키가 없어 질문 음성을 만들 수 없습니다. GOOGLE_TTS_CREDENTIALS를 확인하세요.");
            throw new BusinessException(AnswerErrorCode.TTS_FAILED);
        }

        String accessToken;
        try {
            // 토큰은 약 1시간 유효하다. 만료가 가까울 때만 새로 받는다.
            credentials.refreshIfExpired();
            accessToken = credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            log.warn("Google 액세스 토큰 발급 실패: {}", e.getMessage());
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
                    .header("Authorization", "Bearer " + accessToken)
                    .header("x-goog-user-project", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SynthesizeResponse.class);
        } catch (RestClientException e) {
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
