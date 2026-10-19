package com.benly.question.service;

import com.benly.global.exception.BusinessException;
import com.benly.question.client.ClaudeClient;
import com.benly.question.client.WhisperClient;
import com.benly.question.dto.AnswerCreateRequest;
import com.benly.question.dto.AnswerResponse;
import com.benly.question.dto.FollowUpResult;
import com.benly.question.entity.FollowUpStrategy;
import com.benly.question.entity.Answer;
import com.benly.question.entity.AnswerType;
import com.benly.question.entity.NextActionType;
import com.benly.question.entity.Question;
import com.benly.question.exception.AnswerErrorCode;
import com.benly.session.entity.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerServiceTest {

    @Mock private AnswerCommandService answerCommandService;
    @Mock private ClaudeClient claudeClient;
    @Mock private WhisperClient whisperClient;

    @InjectMocks
    private AnswerService answerService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;
    private static final Long QUESTION_ID = 100L;
    private static final String VALID_TRANSCRIPT = "재고 차감 로직에서 동시 요청이 몰리는 문제가 있었습니다";

    private Answer mockAnswer() {
        Answer answer = mock(Answer.class);
        Question question = mock(Question.class);
        Session session = mock(Session.class);

        given(answer.getTranscript()).willReturn(VALID_TRANSCRIPT);
        given(answer.getQuestion()).willReturn(question);
        given(question.getSession()).willReturn(session);
        given(question.getParent()).willReturn(null);
        given(answer.getInputType()).willReturn(AnswerType.TEXT);
        given(answer.getSttStatus()).willReturn("COMPLETED");

        return answer;
    }

    @Test
    @DisplayName("성공 - 텍스트 답변 저장")
    void success() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);
        Answer mockAnswer = mockAnswer();
        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.FOLLOW_UP, 200L);

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.buildContext(any())).willReturn("context");
        given(claudeClient.generateFollowUp(anyString(), any(), any()))
                .willReturn(new FollowUpResult("꼬리질문입니다", FollowUpStrategy.WHY));
        given(answerCommandService.saveFollowUpQuestion(any(), any(), anyInt(), any(FollowUpResult.class)))
                .willReturn(mockNextAction);

        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        verify(answerCommandService).saveTextAnswer(SESSION_ID, USER_ID, request);
        assertThat(response.answer().transcript()).contains("재고 차감");
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.FOLLOW_UP);
    }

    @Test
    @DisplayName("실패 - 질문이 없으면 QUESTION_NOT_FOUND")
    void fail_questionNotFound() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.QUESTION_NOT_FOUND));

        assertThatThrownBy(() -> answerService.submitTextAnswer(SESSION_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    @DisplayName("실패 - URL 세션과 질문의 세션이 다르면 QUESTION_SESSION_MISMATCH")
    void fail_sessionMismatch() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);
        Long wrongSessionId = 999L;

        given(answerCommandService.saveTextAnswer(wrongSessionId, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.QUESTION_SESSION_MISMATCH));

        assertThatThrownBy(() -> answerService.submitTextAnswer(wrongSessionId, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.QUESTION_SESSION_MISMATCH);
    }

    @Test
    @DisplayName("실패 - 남의 세션이면 ANSWER_FORBIDDEN")
    void fail_forbidden() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.ANSWER_FORBIDDEN));

        assertThatThrownBy(() -> answerService.submitTextAnswer(SESSION_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.ANSWER_FORBIDDEN);
    }

    @Test
    @DisplayName("실패 - IN_PROGRESS가 아니면 SESSION_NOT_IN_PROGRESS")
    void fail_notInProgress() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.SESSION_NOT_IN_PROGRESS));

        assertThatThrownBy(() -> answerService.submitTextAnswer(SESSION_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.SESSION_NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("실패 - 이미 답변했으면 ALREADY_ANSWERED")
    void fail_alreadyAnswered() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, VALID_TRANSCRIPT);

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.ALREADY_ANSWERED));

        assertThatThrownBy(() -> answerService.submitTextAnswer(SESSION_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.ALREADY_ANSWERED);
    }

    @Test
    @DisplayName("실패 - 답변이 너무 짧으면 ANSWER_TOO_SHORT")
    void fail_tooShort() {
        AnswerCreateRequest request = new AnswerCreateRequest(QUESTION_ID, "짧음");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request))
                .willThrow(new BusinessException(AnswerErrorCode.ANSWER_TOO_SHORT));

        assertThatThrownBy(() -> answerService.submitTextAnswer(SESSION_ID, USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AnswerErrorCode.ANSWER_TOO_SHORT);
    }
}