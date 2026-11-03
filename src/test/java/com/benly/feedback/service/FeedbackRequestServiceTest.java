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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final LocalDateTime THRESHOLD = LocalDateTime.of(2026, 11, 3, 12, 0);

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

    private ScoringRequestedEvent publishedEvent() {
        ArgumentCaptor<ScoringRequestedEvent> captor = ArgumentCaptor.forClass(ScoringRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("COMPLETED 세션은 ANALYZING 전환과 SCORING 기록 생성 후 시도 번호를 담아 이벤트를 발행한다")
    void firstScoring() {
        // given
        Session s = session(SessionStatus.COMPLETED);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));

        // when
        service.requestScoring(USER_ID, SESSION_ID);

        // then
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ANALYZING);
        ArgumentCaptor<SessionFeedback> saved = ArgumentCaptor.forClass(SessionFeedback.class);
        verify(sessionFeedbackRepository).saveAndFlush(saved.capture());
        ScoringRequestedEvent event = publishedEvent();
        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.attempt()).isEqualTo(saved.getValue().getScoringStartedAt());
    }

    @Test
    @DisplayName("이미 채점 중이고 기준 시간 전이면 거부하고 이벤트를 발행하지 않는다")
    void rejectWhenScoring() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.SCORING)));
        given(staleTaskPolicy.scoringThreshold()).willReturn(THRESHOLD);
        given(sessionFeedbackRepository.restartStaleScoring(eq(SESSION_ID), eq(THRESHOLD), any()))
                .willReturn(0);

        // when & then
        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.ALREADY_SCORED);
        verify(sessionFeedbackRepository).restartStaleScoring(eq(SESSION_ID), eq(THRESHOLD), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("채점 중이지만 멈춘 건이면 새 시도로 다시 채점하고 이벤트를 발행한다")
    void restartWhenStale() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.SCORING)));
        given(staleTaskPolicy.scoringThreshold()).willReturn(THRESHOLD);
        given(sessionFeedbackRepository.restartStaleScoring(eq(SESSION_ID), eq(THRESHOLD), any()))
                .willReturn(1);

        // when
        service.requestScoring(USER_ID, SESSION_ID);

        // then
        ArgumentCaptor<LocalDateTime> attempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionFeedbackRepository).restartStaleScoring(eq(SESSION_ID), eq(THRESHOLD), attempt.capture());
        assertThat(publishedEvent().attempt()).isEqualTo(attempt.getValue());
    }

    @Test
    @DisplayName("직전 실패면 원자적 재시작에 성공한 요청만 새 시도 번호로 이벤트를 발행한다")
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
        ArgumentCaptor<LocalDateTime> attempt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sessionFeedbackRepository).restartScoring(
                eq(SESSION_ID), eq(FeedbackStatus.FAILED), eq(FeedbackStatus.SCORING), attempt.capture());
        assertThat(publishedEvent().attempt()).isEqualTo(attempt.getValue());
    }

    @Test
    @DisplayName("동시 재채점에서 원자적 전환에 진 요청은 거부한다")
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
    @DisplayName("이미 완료된 채점은 거부한다")
    void rejectWhenCompleted() {
        // given
        Session s = session(SessionStatus.ANALYZING);
        given(sessionReadRepository.findById(SESSION_ID)).willReturn(Optional.of(s));
        given(sessionFeedbackRepository.findBySession_Id(SESSION_ID))
                .willReturn(Optional.of(feedback(s, FeedbackStatus.COMPLETED)));

        // when & then
        assertThatThrownBy(() -> service.requestScoring(USER_ID, SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedbackErrorCode.ALREADY_SCORED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("면접이 끝나지 않은 세션은 거부한다")
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
}
