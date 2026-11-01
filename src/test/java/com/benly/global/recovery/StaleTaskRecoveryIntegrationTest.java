package com.benly.global.recovery;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.repository.SessionFeedbackRepository;
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import com.benly.session.repository.SessionRepository;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StaleTaskRecoveryIntegrationTest {

    @Autowired private SessionFeedbackRepository sessionFeedbackRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;

    private Session session(SessionStatus status) {
        User user = userRepository.save(User.of("stale-test-" + System.nanoTime(), "테스터"));
        Session session = Session.create(user, "SERVICE", "TECHNICAL", "백엔드", "카카오");
        org.springframework.test.util.ReflectionTestUtils.setField(session, "status", status);
        return sessionRepository.save(session);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 시작한 채점만 FAILED로 바뀐다")
    void failStaleScoring() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));

        int notYet = sessionFeedbackRepository.failStaleScoring(LocalDateTime.now().minusMinutes(5));
        int stale = sessionFeedbackRepository.failStaleScoring(LocalDateTime.now().plusMinutes(1));

        assertThat(notYet).isZero();
        assertThat(stale).isEqualTo(1);
        assertThat(sessionFeedbackRepository.findById(sf.getId()).orElseThrow().getStatus())
                .isEqualTo(FeedbackStatus.FAILED);
    }

    @Test
    @DisplayName("완료된 채점은 오래됐어도 바뀌지 않는다")
    void completedNotTouched() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = SessionFeedback.startScoring(session);
        sf.complete(80, "총평", "코칭", "액션");
        sessionFeedbackRepository.save(sf);

        int changed = sessionFeedbackRepository.failStaleScoring(LocalDateTime.now().plusMinutes(1));

        assertThat(changed).isZero();
    }

    @Test
    @DisplayName("멈춘 채점을 다시 시작하면 시작 시각이 새로 찍힌다")
    void restartStaleScoring() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));
        LocalDateTime now = LocalDateTime.now().plusMinutes(2).truncatedTo(ChronoUnit.MICROS);

        int restarted = sessionFeedbackRepository.restartStaleScoring(
                session.getId(), LocalDateTime.now().plusMinutes(1), now);

        assertThat(restarted).isEqualTo(1);
        SessionFeedback reloaded = sessionFeedbackRepository.findById(sf.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.SCORING);
        assertThat(reloaded.getScoringStartedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("기준 시각보다 먼저 만든 질문 생성 중 세션만 FAILED로 바뀐다")
    void failStaleGenerating() {
        Session generating = session(SessionStatus.GENERATING);
        Session ready = session(SessionStatus.READY);

        int changed = sessionRepository.failStaleGenerating(LocalDateTime.now().plusMinutes(1));

        assertThat(changed).isGreaterThanOrEqualTo(1);
        assertThat(sessionRepository.findById(generating.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.FAILED);
        assertThat(sessionRepository.findById(ready.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.READY);
    }
}
