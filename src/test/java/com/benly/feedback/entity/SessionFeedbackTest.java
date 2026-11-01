package com.benly.feedback.entity;

import com.benly.session.entity.Session;
import com.benly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionFeedbackTest {

    private SessionFeedback scoringStartedAt(LocalDateTime startedAt) {
        Session session = Session.create(mock(User.class), "SERVICE", "TECHNICAL", "백엔드", "카카오");
        SessionFeedback sf = SessionFeedback.startScoring(session);
        ReflectionTestUtils.setField(sf, "scoringStartedAt", startedAt);
        return sf;
    }

    @Test
    @DisplayName("채점 시작 시 시작 시각이 기록된다")
    void startRecordsTime() {
        Session session = Session.create(mock(User.class), "SERVICE", "TECHNICAL", "백엔드", "카카오");
        SessionFeedback sf = SessionFeedback.startScoring(session);

        assertThat(sf.getScoringStartedAt()).isNotNull();
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
}
