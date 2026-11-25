package com.benly.question.service;

import com.benly.global.exception.BusinessException;
import com.benly.question.client.GeminiTtsClient;
import com.benly.question.entity.Question;
import com.benly.question.exception.AnswerErrorCode;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.entity.Session;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class QuestionAudioServiceTest {

    private static final Long QUESTION_ID = 1L;
    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final String CONTENT = "최근 프로젝트에서 동시성 문제를 해결한 경험을 말씀해 주세요.";

    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final GeminiTtsClient geminiTtsClient = mock(GeminiTtsClient.class);
    private final QuestionAudioService questionAudioService = new QuestionAudioService(
            new QuestionAudioQueryService(questionRepository), geminiTtsClient);

    private Question ownedQuestion() {
        User owner = mock(User.class);
        given(owner.getId()).willReturn(OWNER_ID);
        Session session = mock(Session.class);
        given(session.getUser()).willReturn(owner);
        Question question = mock(Question.class);
        given(question.getSession()).willReturn(session);
        given(question.getContent()).willReturn(CONTENT);
        return question;
    }

    @Test
    @DisplayName("본인 세션의 질문이면 질문 문장으로 음성을 만든다")
    void createQuestionAudio_owner() {
        Question question = ownedQuestion();
        given(questionRepository.findById(QUESTION_ID)).willReturn(Optional.of(question));
        byte[] mp3 = {1, 2, 3};
        given(geminiTtsClient.synthesize(CONTENT)).willReturn(mp3);

        byte[] result = questionAudioService.createQuestionAudio(QUESTION_ID, OWNER_ID);

        assertThat(result).isEqualTo(mp3);
    }

    @Test
    @DisplayName("다른 사람 세션의 질문이면 403이고 TTS를 호출하지 않는다")
    void createQuestionAudio_otherUser() {
        Question question = ownedQuestion();
        given(questionRepository.findById(QUESTION_ID)).willReturn(Optional.of(question));

        assertThatThrownBy(() -> questionAudioService.createQuestionAudio(QUESTION_ID, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnswerErrorCode.ANSWER_FORBIDDEN);
        verify(geminiTtsClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("없는 질문이면 404이고 TTS를 호출하지 않는다")
    void createQuestionAudio_notFound() {
        given(questionRepository.findById(QUESTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> questionAudioService.createQuestionAudio(QUESTION_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnswerErrorCode.QUESTION_NOT_FOUND);
        verify(geminiTtsClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("서비스 계정 키가 없으면 외부 호출 없이 TTS 실패 에러를 던진다")
    void synthesize_withoutCredentials() {
        GeminiTtsClient client = new GeminiTtsClient("", "https://example.invalid", "gemini-2.5-flash-tts",
                "Algieba", "ko-KR");

        assertThatThrownBy(() -> client.synthesize(CONTENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnswerErrorCode.TTS_FAILED);
    }

    @Test
    @DisplayName("서비스 계정 키가 잘못돼도 앱은 뜨고, 호출 시 TTS 실패 에러를 던진다")
    void synthesize_withInvalidCredentials() {
        GeminiTtsClient client = new GeminiTtsClient("not-a-valid-key", "https://example.invalid",
                "gemini-2.5-flash-tts", "Algieba", "ko-KR");

        assertThatThrownBy(() -> client.synthesize(CONTENT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnswerErrorCode.TTS_FAILED);
    }
}
