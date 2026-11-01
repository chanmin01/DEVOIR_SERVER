package com.benly.feedback.service;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.event.ScoringRequestedEvent;
import com.benly.feedback.exception.FeedbackErrorCode;
import com.benly.feedback.repository.SessionFeedbackRepository;
import com.benly.feedback.repository.SessionReadRepository;
import com.benly.global.exception.BusinessException;
import com.benly.global.recovery.StaleTaskPolicy;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackRequestServiceTest {

    @Mock private SessionReadRepository sessionReadRepository;
    @Mock private SessionFeedbackRepository sessionFeedbackRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StaleTaskPolicy staleTaskPolicy;

    @InjectMocks
    private FeedbackRequestService service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 8L;

    private Session session(SessionStatus status) {
        User owner = mock(User.class);
        given(owner.getId()).willReturn(USER_ID);
        Session s = Session.create(owner, "IT", "PERSONALITY", "백엔드", "카카오");
        ReflectionTestUtils.setField(s, "id", SESSION_ID);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    private SessionFeedback feedback(Session s, FeedbackStatus status) {
        SessionFeedback sf = SessionFeedback.startScoring(s);
        ReflectionTestUtils.setField(sf, "status", status);
        return sf;
    }

    @Test
    @DisplayName("COMPLETED 세션은 ANALYZING 전환·SCORING 기록 생성 후 채점 이벤트를 발행한다")
    void firstScoring() {
        // given
        Session s = session(SessionStatus.COMPLETED);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));

        // when
        service.requestScoring(USER_ID, SESSION_ID);

        // then
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ANALYZING);
        verify(sessionFeedbackRepository).saveAndFlush(any(SessionFeedback.class));
        verify(eventPublisher).publishEvent(any(ScoringRequestedEvent.class));
    }

    @Test
    @DisplayName("이미 채점 중(SCORING)이면 거부하고 이벤트를 발행하지 않는다")
    void rejectWhenScoring() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.SCORING)));

        // when & then
        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.ALREADY_SCORED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("직전 실패(FAILED)면 원자적 재시작에 성공(1)한 요청만 이벤트를 발행한다")
    void rescoreWhenFailed() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.FAILED)));
        given(sessionFeedbackRepository.restartScoring(eq(SESSION_ID), eq(FeedbackStatus.FAILED), eq(FeedbackStatus.SCORING), any()))
                .willReturn(1);

        // when
        service.requestScoring(USER_ID, SESSION_ID);

        // then
        verify(eventPublisher).publishEvent(any(ScoringRequestedEvent.class));
    }

    @Test
    @DisplayName("동시 재채점에서 원자적 전환에 진 요청(0)은 거부한다")
    void rescoreLoserRejected() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.FAILED)));
        given(sessionFeedbackRepository.restartScoring(eq(SESSION_ID), eq(FeedbackStatus.FAILED), eq(FeedbackStatus.SCORING), any()))
                .willReturn(0);

        // when & then
        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.ALREADY_SCORED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("면접이 끝나지 않은 세션(IN_PROGRESS)은 거부한다")
    void rejectWhenNotFinished() {
        // given
        Session s = session(SessionStatus.IN_PROGRESS);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));

        // when & then
        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.SESSION_NOT_FINISHED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("다른 사용자의 세션이면 거부한다")
    void rejectWhenNotOwner() {
        // given
        Session s = session(SessionStatus.COMPLETED);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));

        // when & then
        assertThatThrownBy(() -> service.requestScoring(999L, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.SESSION_FORBIDDEN);
    }

    @Test
    @DisplayName("채점 중이지만 멈춘 건이면 다시 채점하고 이벤트를 발행한다")
    void restartWhenStale() {
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.SCORING)));
        given(sessionFeedbackRepository.restartStaleScoring(eq(SESSION_ID), any(), any()))
                .willReturn(1);

        service.requestScoring(USER_ID, SESSION_ID);

        verify(eventPublisher).publishEvent(any(ScoringRequestedEvent.class));
    }

    @Test
    @DisplayName("채점 중이고 아직 기준 시간 전이면 거부한다")
    void rejectWhenScoringNotStale() {
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.SCORING)));
        given(sessionFeedbackRepository.restartStaleScoring(eq(SESSION_ID), any(), any()))
                .willReturn(0);

        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.ALREADY_SCORED);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
