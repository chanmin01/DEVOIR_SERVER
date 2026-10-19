package com.benly.question.service;

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
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 불필요한 스터빙 에러 방지
class AnswerFollowUpTest {

    @Mock private ClaudeClient claudeClient;
    @Mock private WhisperClient whisperClient;
    @Mock private AnswerCommandService answerCommandService;

    @InjectMocks
    private AnswerService answerService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    private User mockUser() {
        User user = mock(User.class);
        given(user.getId()).willReturn(USER_ID);
        return user;
    }

    private Session mockSession() {
        Session session = mock(Session.class);
        User user = mockUser();
        given(session.getId()).willReturn(SESSION_ID);
        given(session.getUser()).willReturn(user);
        given(session.getStatus()).willReturn(SessionStatus.IN_PROGRESS);
        return session;
    }

    // 공통적으로 사용되는 Answer 모킹 헬퍼 메서드
    private Answer mockSavedAnswer(Question question, String transcript, String inputType) {
        Answer answer = mock(Answer.class);
        given(answer.getQuestion()).willReturn(question);
        given(answer.getTranscript()).willReturn(transcript);
        given(answer.getInputType()).willReturn(AnswerType.valueOf(inputType)); // 💡 세미콜론 중복 오타 수정
        given(answer.getSttStatus()).willReturn("COMPLETED");
        return answer;
    }

    @Test
    @DisplayName("메인 질문에 답변하면 꼬리1 생성 → FOLLOW_UP")
    void mainAnswer_createsFollowUp() {
        // given
        Session session = mockSession();
        Question mainQuestion = mock(Question.class);
        given(mainQuestion.getId()).willReturn(100L);
        given(mainQuestion.getParent()).willReturn(null);
        given(mainQuestion.getSession()).willReturn(session);

        AnswerCreateRequest request = new AnswerCreateRequest(100L, "메인 질문에 대한 충분한 답변입니다");
        Answer mockAnswer = mockSavedAnswer(mainQuestion, request.transcript(), "TEXT");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.buildContext(100L)).willReturn("context");
        given(claudeClient.generateFollowUp(anyString(), any(), any()))
                .willReturn(new FollowUpResult("꼬리질문입니다", FollowUpStrategy.WHY));

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.FOLLOW_UP, 200L);
        given(answerCommandService.saveFollowUpQuestion(eq(SESSION_ID), eq(100L), eq(1), any(FollowUpResult.class)))
                .willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        // then
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.FOLLOW_UP);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("꼬리1에 답변하면 꼬리2 생성 → FOLLOW_UP")
    void followUp1Answer_createsFollowUp2() {
        // given
        Session session = mockSession();
        Question mainQuestion = mock(Question.class);
        given(mainQuestion.getId()).willReturn(100L);
        Question followUp1 = mock(Question.class);
        given(followUp1.getId()).willReturn(200L);
        given(followUp1.getParent()).willReturn(mainQuestion);
        given(followUp1.getSession()).willReturn(session);

        AnswerCreateRequest request = new AnswerCreateRequest(200L, "꼬리1에 대한 답변입니다");
        Answer mockAnswer = mockSavedAnswer(followUp1, request.transcript(), "TEXT");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.countFollowUps(100L)).willReturn(1);
        given(answerCommandService.buildContext(100L)).willReturn("context");
        given(claudeClient.generateFollowUp(anyString(), any(), any()))
                .willReturn(new FollowUpResult("꼬리질문2", FollowUpStrategy.DEEP));

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.FOLLOW_UP, 300L);
        given(answerCommandService.saveFollowUpQuestion(eq(SESSION_ID), eq(100L), eq(2), any(FollowUpResult.class)))
                .willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        // then
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.FOLLOW_UP);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(300L);
    }

    @Test
    @DisplayName("꼬리2에 답변하면 다음 메인 → NEXT_MAIN")
    void followUp2Answer_nextMain() {
        // given
        Session session = mockSession();
        Question mainQuestion = mock(Question.class);
        given(mainQuestion.getId()).willReturn(100L); // ID 명시
        Question followUp2 = mock(Question.class);
        given(followUp2.getId()).willReturn(300L);
        given(followUp2.getParent()).willReturn(mainQuestion);
        given(followUp2.getSession()).willReturn(session);

        AnswerCreateRequest request = new AnswerCreateRequest(300L, "꼬리2에 대한 답변입니다");
        Answer mockAnswer = mockSavedAnswer(followUp2, request.transcript(), "TEXT");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.countFollowUps(100L)).willReturn(2);

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.NEXT_MAIN, 101L);

        // 💡 수정됨: 엔티티 대신 ID를 파라미터로 넘기도록 변경
        given(answerCommandService.decideNextMainOrFinish(100L, SESSION_ID)).willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        // then
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.NEXT_MAIN);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("마지막 메인의 꼬리2에 답변하면 → FINISH")
    void lastFollowUp2Answer_finish() {
        // given
        Session session = mockSession();
        Question mainQuestion = mock(Question.class);
        given(mainQuestion.getId()).willReturn(100L);
        Question followUp2 = mock(Question.class);
        given(followUp2.getId()).willReturn(300L);
        given(followUp2.getParent()).willReturn(mainQuestion);
        given(followUp2.getSession()).willReturn(session);

        AnswerCreateRequest request = new AnswerCreateRequest(300L, "마지막 꼬리질문에 대한 충분한 길이의 답변입니다.");
        Answer mockAnswer = mockSavedAnswer(followUp2, request.transcript(), "TEXT");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.countFollowUps(100L)).willReturn(2);

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.FINISH, null);

        // 💡 수정됨: 엔티티 대신 ID를 파라미터로 넘기도록 변경
        given(answerCommandService.decideNextMainOrFinish(100L, SESSION_ID)).willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        // then
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.FINISH);
        assertThat(response.nextAction().nextQuestionId()).isNull();
    }

    @Test
    @DisplayName("꼬리 생성 실패해도 답변 저장 + 다음 메인으로")
    void followUpFails_stillProceeds() {
        // given
        Session session = mockSession();
        Question mainQuestion = mock(Question.class);
        given(mainQuestion.getId()).willReturn(100L);
        given(mainQuestion.getParent()).willReturn(null);
        given(mainQuestion.getSession()).willReturn(session);

        AnswerCreateRequest request = new AnswerCreateRequest(100L, "메인 질문에 대한 답변입니다");
        Answer mockAnswer = mockSavedAnswer(mainQuestion, request.transcript(), "TEXT");

        given(answerCommandService.saveTextAnswer(SESSION_ID, USER_ID, request)).willReturn(mockAnswer);
        given(answerCommandService.buildContext(100L)).willReturn("context");
        given(claudeClient.generateFollowUp(anyString(), any(), any())).willThrow(new RuntimeException("Claude 오류"));

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.NEXT_MAIN, 101L);

        // 💡 수정됨: 엔티티 대신 ID를 파라미터로 넘기도록 변경
        given(answerCommandService.decideNextMainOrFinish(100L, SESSION_ID)).willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitTextAnswer(SESSION_ID, USER_ID, request);

        // then
        assertThat(response.nextAction().type()).isEqualTo(NextActionType.NEXT_MAIN);
        assertThat(response.answer()).isNotNull();
    }

    @Test
    @DisplayName("음성 답변 → Whisper 변환 후 저장 (AUDIO)")
    void audioAnswer_transcribesAndSaves() {
        // given
        Session session = mockSession();
        Question main = mock(Question.class);
        given(main.getId()).willReturn(100L);
        given(main.getParent()).willReturn(null);
        given(main.getSession()).willReturn(session);

        MultipartFile audioFile = new MockMultipartFile("audio", "answer.m4a", "audio/m4a", "dummy audio bytes".getBytes());
        String transcribedText = "음성에서 변환된 충분한 길이의 답변입니다";
        Integer durationSec = 10;

        given(whisperClient.transcribe(any(MultipartFile.class))).willReturn(transcribedText);

        Answer mockAnswer = mockSavedAnswer(main, transcribedText, "AUDIO");
        given(answerCommandService.saveAudioAnswer(SESSION_ID, USER_ID, 100L, transcribedText, durationSec)).willReturn(mockAnswer);

        given(answerCommandService.buildContext(100L)).willReturn("context");
        given(claudeClient.generateFollowUp(anyString(), any(), any()))
                .willReturn(new FollowUpResult("꼬리질문", FollowUpStrategy.WHY));

        AnswerResponse.NextAction mockNextAction = AnswerResponse.NextAction.of(NextActionType.FOLLOW_UP, 200L);
        given(answerCommandService.saveFollowUpQuestion(eq(SESSION_ID), eq(100L), eq(1), any(FollowUpResult.class)))
                .willReturn(mockNextAction);

        // when
        AnswerResponse response = answerService.submitAudioAnswer(SESSION_ID, USER_ID, 100L, audioFile, durationSec);

        // then
        // 💡 추가됨: 보안 검증 로직이 정상 호출되었는지 검증
        verify(answerCommandService).validateBeforeStt(100L, SESSION_ID, USER_ID);

        assertThat(response.answer().inputType()).isEqualTo("AUDIO");
        assertThat(response.answer().transcript()).contains("변환된");
        assertThat(response.answer().sttStatus()).isEqualTo("COMPLETED");
    }
}