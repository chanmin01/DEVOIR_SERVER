package com.benly.question.service;

import com.benly.question.client.ClaudeClient;
import com.benly.question.client.WhisperClient;
import com.benly.question.dto.AnswerResponse;
import com.benly.question.dto.FollowUpResult;
import com.benly.question.entity.FollowUpStrategy;
import com.benly.question.entity.Answer;
import com.benly.question.entity.AnswerType;
import com.benly.question.entity.NextActionType;
import com.benly.question.entity.Question;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnswerSkipTest {

    @Mock private ClaudeClient claudeClient;
    @Mock private WhisperClient whisperClient;
    @Mock private AnswerCommandService answerCommandService;

    @InjectMocks
    private AnswerService answerService;

    private static final Long SESSION_ID = 10L;
    private static final Long USER_ID = 1L;

    private Answer mockSkipAnswer(Question question) {
        Answer skip = mock(Answer.class);
        given(skip.getQuestion()).willReturn(question);
        given(skip.getId()).willReturn(999L);
        given(skip.getInputType()).willReturn(AnswerType.SKIP);
        given(skip.getTranscript()).willReturn(null);
        return skip;
    }

    @Test
    @DisplayName("메인 스킵 → 다음 메인 (꼬리 생성 안 함)")
    void skipMain_nextMain() {
        Session session = mock(Session.class);
        given(session.getId()).willReturn(SESSION_ID);

        Question main = mock(Question.class);
        given(main.getId()).willReturn(100L);
        given(main.getParent()).willReturn(null);          // 메인
        given(main.getSession()).willReturn(session);

        Answer skip = mockSkipAnswer(main);
        given(answerCommandService.saveSkip(SESSION_ID, USER_ID, 100L)).willReturn(skip);

        // 메인 스킵 → decideNextMainOrFinish 호출됨 → NEXT_MAIN
        given(answerCommandService.decideNextMainOrFinish(100L, SESSION_ID))
                .willReturn(AnswerResponse.NextAction.of(NextActionType.NEXT_MAIN, 101L));

        AnswerResponse response = answerService.skipQuestion(SESSION_ID, USER_ID, 100L);

        assertThat(response.nextAction().type()).isEqualTo(NextActionType.NEXT_MAIN);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("꼬리1 스킵 → 새 꼬리 생성 (FOLLOW_UP)")
    void skipFollowUp1_createsNewFollowUp() {
        Session session = mock(Session.class);
        given(session.getId()).willReturn(SESSION_ID);

        Question main = mock(Question.class);
        given(main.getId()).willReturn(100L);

        Question followUp1 = mock(Question.class);
        given(followUp1.getParent()).willReturn(main);      // 꼬리
        given(followUp1.getSession()).willReturn(session);

        Answer skip = mockSkipAnswer(followUp1);
        given(answerCommandService.saveSkip(SESSION_ID, USER_ID, 200L)).willReturn(skip);

        // 꼬리1 스킵 시점: 꼬리 1개 (스킵된 꼬리1) → count=1 < 2 → 새 꼬리 생성
        given(answerCommandService.countFollowUps(100L)).willReturn(1);
        given(answerCommandService.buildContext(100L)).willReturn("맥락");
        given(claudeClient.generateFollowUp(anyString(), any(), any()))
                .willReturn(new FollowUpResult("새 꼬리질문", FollowUpStrategy.WHY));
        given(answerCommandService.saveFollowUpQuestion(any(), any(), anyInt(), any(FollowUpResult.class)))
                .willReturn(AnswerResponse.NextAction.of(NextActionType.FOLLOW_UP, 201L));

        AnswerResponse response = answerService.skipQuestion(SESSION_ID, USER_ID, 200L);

        assertThat(response.nextAction().type()).isEqualTo(NextActionType.FOLLOW_UP);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(201L);
    }

    @Test
    @DisplayName("꼬리2 스킵 → 다음 메인 (NEXT_MAIN)")
    void skipFollowUp2_nextMain() {
        Session session = mock(Session.class);
        given(session.getId()).willReturn(SESSION_ID);

        Question main = mock(Question.class);
        given(main.getId()).willReturn(100L);

        Question followUp2 = mock(Question.class);
        given(followUp2.getParent()).willReturn(main);      // 꼬리
        given(followUp2.getSession()).willReturn(session);

        Answer skip = mockSkipAnswer(followUp2);
        given(answerCommandService.saveSkip(SESSION_ID, USER_ID, 300L)).willReturn(skip);

        // 꼬리2 스킵 시점: 꼬리 2개 → count=2, 2 < 2 아님 → 다음 메인
        given(answerCommandService.countFollowUps(100L)).willReturn(2);
        given(answerCommandService.decideNextMainOrFinish(100L, SESSION_ID))
                .willReturn(AnswerResponse.NextAction.of(NextActionType.NEXT_MAIN, 101L));

        AnswerResponse response = answerService.skipQuestion(SESSION_ID, USER_ID, 300L);

        assertThat(response.nextAction().type()).isEqualTo(NextActionType.NEXT_MAIN);
        assertThat(response.nextAction().nextQuestionId()).isEqualTo(101L);
    }
}
