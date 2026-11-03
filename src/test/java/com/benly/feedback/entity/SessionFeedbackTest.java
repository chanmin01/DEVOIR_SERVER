package com.benly.feedback.entity;

import com.benly.session.entity.Session;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionFeedbackTest {

    private SessionFeedback newScoring() {
        Session session = Session.create(mock(User.class), "SERVICE", "TECHNICAL", "백엔드", "카카오");
        return SessionFeedback.startScoring(session);
    }

    private SessionFeedback scoringStartedAt(LocalDateTime startedAt) {
        SessionFeedback sf = newScoring();
        ReflectionTestUtils.setField(sf, "scoringStartedAt", startedAt);
        return sf;
    }

    @Test
    @DisplayName("채점 시작 시 시작 시각이 마이크로초 단위로 기록된다")
    void startRecordsTime() {
        SessionFeedback sf = newScoring();

        assertThat(sf.getScoringStartedAt()).isNotNull();
        assertThat(sf.getScoringStartedAt())
                .isEqualTo(sf.getScoringStartedAt().truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("기준 시각보다 먼저 시작해 아직 채점 중이면 멈춘 것")
    void staleWhenOlderThanThreshold() {
        LocalDateTime now = LocalDateTime.now();
        SessionFeedback sf = scoringStartedAt(now.minusMinutes(10));

        assertThat(sf.isStale(now.minusMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("기준 시각 이후에 시작했으면 정상 진행 중")
    void notStaleWhenRecent() {
        LocalDateTime now = LocalDateTime.now();
        SessionFeedback sf = scoringStartedAt(now.minusMinutes(1));

        assertThat(sf.isStale(now.minusMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("채점 중이 아니면 오래됐어도 멈춘 게 아니다")
    void notStaleWhenNotScoring() {
        LocalDateTime now = LocalDateTime.now();
        SessionFeedback sf = scoringStartedAt(now.minusMinutes(10));
        sf.fail();

        assertThat(sf.isStale(now.minusMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("시작 시각이 없는 옛 데이터는 멈춘 것으로 보지 않는다")
    void notStaleWhenNoStartTime() {
        SessionFeedback sf = scoringStartedAt(null);

        assertThat(sf.isStale(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("시작 시각이 같으면 현재 시도다")
    void currentAttempt() {
        SessionFeedback sf = newScoring();

        assertThat(sf.isCurrentAttempt(sf.getScoringStartedAt())).isTrue();
    }

    @Test
    @DisplayName("재채점으로 시작 시각이 바뀌면 이전 시도는 현재 시도가 아니다")
    void oldAttemptNotCurrent() {
        SessionFeedback sf = newScoring();
        LocalDateTime oldAttempt = sf.getScoringStartedAt();
        ReflectionTestUtils.setField(sf, "scoringStartedAt", oldAttempt.plusMinutes(11));

        assertThat(sf.isCurrentAttempt(oldAttempt)).isFalse();
    }

    @Test
    @DisplayName("채점 중이 아니거나 시도 번호가 없으면 현재 시도가 아니다")
    void notCurrentWhenFailedOrNull() {
        SessionFeedback sf = newScoring();
        LocalDateTime attempt = sf.getScoringStartedAt();

        assertThat(sf.isCurrentAttempt(null)).isFalse();
        sf.fail();
        assertThat(sf.isCurrentAttempt(attempt)).isFalse();
    }
}
