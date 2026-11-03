package com.benly.session.service;

import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
import com.benly.question.dto.CurrentQuestionResponse;
import com.benly.question.entity.Question;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.dto.GenerationStatusResponse;
import com.benly.session.dto.SessionDetailResponse;
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import com.benly.session.exception.SessionErrorCode;
import com.benly.session.repository.SessionRepository;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionQueryTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private StaleTaskPolicy staleTaskPolicy;

    @InjectMocks
    private SessionService sessionService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    private User mockUser(Long id) {
        User user = mock(User.class);
        given(user.getId()).willReturn(id);
        return user;
    }

    private Session createRealSession(User owner, SessionStatus status) {
        Session session = Session.create(owner, "SERVICE", "TECHNICAL", "백엔드", "카카오");
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "status", status);
        return session;
    }

    private void generatingSession(LocalDateTime createdAt, LocalDateTime startedAt) {
        Session session = createRealSession(mockUser(USER_ID), SessionStatus.GENERATING);
        ReflectionTestUtils.setField(session, "createdAt", createdAt);
        ReflectionTestUtils.setField(session, "generationStartedAt", startedAt);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
    }

    private void thresholds(LocalDateTime now) {
        given(staleTaskPolicy.generationThreshold()).willReturn(now.minusMinutes(3));
        given(staleTaskPolicy.generationQueueThreshold()).willReturn(now.minusMinutes(15));
    }

    // ===== 세션 조회 =====

    @Test
    @DisplayName("세션 조회 성공 - 진행도 포함")
    void getSession_success() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.IN_PROGRESS);

        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(questionRepository.countBySessionAndParentIsNull(session)).willReturn(5);
        given(questionRepository.countUnansweredMainsBySessionId(SESSION_ID)).willReturn(2);

        SessionDetailResponse response = sessionService.getSession(USER_ID, SESSION_ID);

        assertThat(response.mainProgress().total()).isEqualTo(5);
        assertThat(response.mainProgress().current()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("세션 조회 실패 - 세션 없으면 SESSION_NOT_FOUND")
    void getSession_notFound() {
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SessionErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("세션 조회 실패 - 남의 세션이면 SESSION_FORBIDDEN")
    void getSession_forbidden() {
        User otherUser = mockUser(2L);
        Session session = createRealSession(otherUser, SessionStatus.IN_PROGRESS);

        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.getSession(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SessionErrorCode.SESSION_FORBIDDEN);
    }

    // ===== 폴링 =====

    @Test
    @DisplayName("생성 상태 폴링 성공 - status 반환")
    void getGenerationStatus_success() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.READY);
        thresholds(LocalDateTime.now());
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("READY");
        verify(sessionRepository, never()).failStaleGeneratingById(any(), any(), any());
    }

    @Test
    @DisplayName("생성 상태 폴링 - 시작 후 기준 시간을 넘기면 DB에 FAILED를 기록하고 FAILED")
    void getGenerationStatus_startedStale() {
        LocalDateTime now = LocalDateTime.now();
        thresholds(now);
        generatingSession(now.minusMinutes(5), now.minusMinutes(4));
        given(sessionRepository.failStaleGeneratingById(eq(SESSION_ID), any(), any())).willReturn(1);

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(sessionRepository).failStaleGeneratingById(SESSION_ID, now.minusMinutes(3), now.minusMinutes(15));
    }

    @Test
    @DisplayName("생성 상태 폴링 - 그사이 워커가 끝냈으면 DB의 현재 상태를 응답")
    void getGenerationStatus_finishedMeanwhile() {
        LocalDateTime now = LocalDateTime.now();
        thresholds(now);
        generatingSession(now.minusMinutes(5), now.minusMinutes(4));
        given(sessionRepository.failStaleGeneratingById(eq(SESSION_ID), any(), any())).willReturn(0);
        given(sessionRepository.findStatusById(SESSION_ID)).willReturn(SessionStatus.READY);

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("READY");
    }

    @Test
    @DisplayName("생성 상태 폴링 - 큐에서 오래 기다렸어도 시작한 지 얼마 안 됐으면 GENERATING")
    void getGenerationStatus_startedRecently() {
        LocalDateTime now = LocalDateTime.now();
        thresholds(now);
        generatingSession(now.minusMinutes(10), now.minusMinutes(1));

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("GENERATING");
        verify(sessionRepository, never()).failStaleGeneratingById(any(), any(), any());
    }

    @Test
    @DisplayName("생성 상태 폴링 - 시작 전이면 대기 기준 시간 전까지 GENERATING")
    void getGenerationStatus_queued() {
        LocalDateTime now = LocalDateTime.now();
        thresholds(now);
        generatingSession(now.minusMinutes(10), null);

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("GENERATING");
        verify(sessionRepository, never()).failStaleGeneratingById(any(), any(), any());
    }

    @Test
    @DisplayName("생성 상태 폴링 - 시작 전인데 대기 기준 시간을 넘기면 DB에 FAILED를 기록하고 FAILED")
    void getGenerationStatus_queuedStale() {
        LocalDateTime now = LocalDateTime.now();
        thresholds(now);
        generatingSession(now.minusMinutes(20), null);
        given(sessionRepository.failStaleGeneratingById(eq(SESSION_ID), any(), any())).willReturn(1);

        GenerationStatusResponse response = sessionService.getGenerationStatus(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo("FAILED");
    }

    // ===== analyze =====

    @Test
    @DisplayName("채점 요청 성공 - COMPLETED -> ANALYZING")
    void analyze_success() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.COMPLETED);

        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sessionRepository.updateStatusIfCurrent(SESSION_ID, SessionStatus.COMPLETED, SessionStatus.ANALYZING))
                .willReturn(1);

        sessionService.analyze(USER_ID, SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ANALYZING);
    }

    @Test
    @DisplayName("채점 요청 실패 - COMPLETED 아니면 SESSION_NOT_COMPLETED")
    void analyze_notCompleted() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.IN_PROGRESS);

        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sessionRepository.updateStatusIfCurrent(SESSION_ID, SessionStatus.COMPLETED, SessionStatus.ANALYZING))
                .willReturn(0);

        assertThatThrownBy(() -> sessionService.analyze(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SessionErrorCode.SESSION_NOT_COMPLETED);
    }

    // ===== 현재 질문 조회 =====

    @Test
    @DisplayName("현재 질문 조회 - 미답변 꼬리 우선")
    void getCurrentQuestion_followUpFirst() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.IN_PROGRESS);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        Question followUp = mock(Question.class);
        given(followUp.getId()).willReturn(200L);
        given(followUp.getSeq()).willReturn(1);
        given(followUp.getContent()).willReturn("꼬리질문");
        given(followUp.getType()).willReturn("FOLLOW_UP");
        given(questionRepository.findFirstUnansweredFollowUpBySessionId(SESSION_ID))
                .willReturn(Optional.of(followUp));

        CurrentQuestionResponse response = sessionService.getCurrentQuestion(USER_ID, SESSION_ID);

        assertThat(response.questionId()).isEqualTo(200L);
        assertThat(response.type()).isEqualTo("FOLLOW_UP");
    }

    @Test
    @DisplayName("현재 질문 조회 - 꼬리 없으면 미답변 메인")
    void getCurrentQuestion_mainWhenNoFollowUp() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.IN_PROGRESS);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));

        Question main = mock(Question.class);
        given(main.getId()).willReturn(100L);
        given(main.getSeq()).willReturn(2);
        given(main.getContent()).willReturn("메인질문");
        given(main.getType()).willReturn("MAIN");
        given(questionRepository.findFirstUnansweredFollowUpBySessionId(SESSION_ID))
                .willReturn(Optional.empty());
        given(questionRepository.findFirstUnansweredMainBySessionId(SESSION_ID))
                .willReturn(Optional.of(main));

        CurrentQuestionResponse response = sessionService.getCurrentQuestion(USER_ID, SESSION_ID);

        assertThat(response.questionId()).isEqualTo(100L);
        assertThat(response.type()).isEqualTo("MAIN");
    }

    // ===== 세션 폐기 =====

    @Test
    @DisplayName("세션 폐기 성공 - IN_PROGRESS -> CANCELED")
    void cancelSession_success() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.IN_PROGRESS);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sessionRepository.updateStatusIfIn(eq(SESSION_ID), anyList(), eq(SessionStatus.CANCELED)))
                .willReturn(1);

        sessionService.cancelSession(USER_ID, SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELED);
    }

    @Test
    @DisplayName("세션 폐기 실패 - COMPLETED는 폐기 불가")
    void cancelSession_completed_fails() {
        User user = mockUser(USER_ID);
        Session session = createRealSession(user, SessionStatus.COMPLETED);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(sessionRepository.updateStatusIfIn(eq(SESSION_ID), anyList(), eq(SessionStatus.CANCELED)))
                .willReturn(0);

        assertThatThrownBy(() -> sessionService.cancelSession(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SessionErrorCode.SESSION_CANNOT_CANCEL);
    }
}
