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
import org.springframework.test.util.ReflectionTestUtils;
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
        ReflectionTestUtils.setField(session, "status", status);
        return sessionRepository.save(session);
    }

    private SessionStatus status(Session s) {
        return sessionRepository.findById(s.getId()).orElseThrow().getStatus();
    }

    private SessionFeedback reload(SessionFeedback sf) {
        return sessionFeedbackRepository.findById(sf.getId()).orElseThrow();
    }

    // ===== 채점 =====

    @Test
    @DisplayName("기준 시각보다 먼저 시작한 채점만 FAILED로 바뀐다")
    void failStaleScoring() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));

        int notYet = sessionFeedbackRepository.failStaleScoring(LocalDateTime.now().minusMinutes(5));
        int stale = sessionFeedbackRepository.failStaleScoring(LocalDateTime.now().plusMinutes(1));

        assertThat(notYet).isZero();
        assertThat(stale).isEqualTo(1);
        assertThat(reload(sf).getStatus()).isEqualTo(FeedbackStatus.FAILED);
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
        LocalDateTime attempt = LocalDateTime.now().plusMinutes(2).truncatedTo(ChronoUnit.MICROS);

        int restarted = sessionFeedbackRepository.restartStaleScoring(
                session.getId(), LocalDateTime.now().plusMinutes(1), attempt);

        assertThat(restarted).isEqualTo(1);
        SessionFeedback reloaded = reload(sf);
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.SCORING);
        assertThat(reloaded.getScoringStartedAt()).isEqualTo(attempt);
    }

    @Test
    @DisplayName("실패한 채점을 다시 시작하면 SCORING이 되고 시작 시각이 새로 찍힌다")
    void restartFailedScoring() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = SessionFeedback.startScoring(session);
        sf.fail();
        sessionFeedbackRepository.save(sf);
        LocalDateTime attempt = LocalDateTime.now().plusMinutes(2).truncatedTo(ChronoUnit.MICROS);

        int restarted = sessionFeedbackRepository.restartScoring(
                session.getId(), FeedbackStatus.FAILED, FeedbackStatus.SCORING, attempt);

        assertThat(restarted).isEqualTo(1);
        SessionFeedback reloaded = reload(sf);
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.SCORING);
        assertThat(reloaded.getScoringStartedAt()).isEqualTo(attempt);
    }

    @Test
    @DisplayName("내 시도면 실패 처리된다")
    void failCurrentAttempt() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));

        int failed = sessionFeedbackRepository.failAttempt(session.getId(), sf.getScoringStartedAt());

        assertThat(failed).isEqualTo(1);
        assertThat(reload(sf).getStatus()).isEqualTo(FeedbackStatus.FAILED);
    }

    @Test
    @DisplayName("이전 시도는 실패 처리로 새 시도를 덮지 못한다")
    void oldAttemptCannotFail() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));
        LocalDateTime oldAttempt = sf.getScoringStartedAt();
        LocalDateTime newAttempt = oldAttempt.plusMinutes(11);
        sessionFeedbackRepository.restartStaleScoring(session.getId(), oldAttempt.plusMinutes(10), newAttempt);

        int failed = sessionFeedbackRepository.failAttempt(session.getId(), oldAttempt);

        assertThat(failed).isZero();
        SessionFeedback reloaded = reload(sf);
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.SCORING);
        assertThat(reloaded.getScoringStartedAt()).isEqualTo(newAttempt);
    }

    @Test
    @DisplayName("잠금 조회로 채점 기록을 가져온다")
    void findForUpdate() {
        Session session = session(SessionStatus.ANALYZING);
        SessionFeedback sf = sessionFeedbackRepository.save(SessionFeedback.startScoring(session));

        assertThat(sessionFeedbackRepository.findBySessionIdForUpdate(session.getId()))
                .get()
                .extracting(SessionFeedback::getId)
                .isEqualTo(sf.getId());
    }

    // ===== 질문 생성 =====

    @Test
    @DisplayName("시작 시각이 기준보다 오래된 질문 생성만 FAILED로 바뀐다")
    void failStaleGenerating() {
        Session stale = session(SessionStatus.GENERATING);
        Session fresh = session(SessionStatus.GENERATING);
        Session ready = session(SessionStatus.READY);
        LocalDateTime now = LocalDateTime.now();
        sessionRepository.markGenerationStarted(stale.getId(), now.minusMinutes(10));
        sessionRepository.markGenerationStarted(fresh.getId(), now);

        sessionRepository.failStaleGenerating(now.minusMinutes(3), now.minusMinutes(15));

        assertThat(status(stale)).isEqualTo(SessionStatus.FAILED);
        assertThat(status(fresh)).isEqualTo(SessionStatus.GENERATING);
        assertThat(status(ready)).isEqualTo(SessionStatus.READY);
    }

    @Test
    @DisplayName("방금 만든 대기 중 생성은 대기 기준 시간 전까지 그대로 둔다")
    void queuedGenerationKept() {
        Session queued = session(SessionStatus.GENERATING);
        LocalDateTime now = LocalDateTime.now();

        sessionRepository.failStaleGenerating(now.plusMinutes(1), now.minusMinutes(15));

        assertThat(status(queued)).isEqualTo(SessionStatus.GENERATING);
    }

    @Test
    @DisplayName("대기 기준 시간을 넘긴 시작 전 생성은 FAILED로 바뀐다")
    void queuedGenerationExpired() {
        Session queued = session(SessionStatus.GENERATING);
        LocalDateTime now = LocalDateTime.now();

        sessionRepository.failStaleGenerating(now.minusMinutes(3), now.plusMinutes(1));

        assertThat(status(queued)).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    @DisplayName("정리된 세션에는 생성 시작 시각을 찍지 않는다")
    void markGenerationStartedOnlyWhenGenerating() {
        Session failed = session(SessionStatus.FAILED);
        Session generating = session(SessionStatus.GENERATING);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        int skipped = sessionRepository.markGenerationStarted(failed.getId(), now);
        int marked = sessionRepository.markGenerationStarted(generating.getId(), now);

        assertThat(skipped).isZero();
        assertThat(marked).isEqualTo(1);
        assertThat(sessionRepository.findById(generating.getId()).orElseThrow().getGenerationStartedAt())
                .isEqualTo(now);
    }

    @Test
    @DisplayName("멈춤으로 정리된 세션은 늦게 끝난 생성이 READY로 되살리지 못한다")
    void failedSessionNotRevived() {
        Session session = session(SessionStatus.FAILED);

        int updated = sessionRepository.updateStatusIfCurrent(
                session.getId(), SessionStatus.GENERATING, SessionStatus.READY);

        assertThat(updated).isZero();
        assertThat(status(session)).isEqualTo(SessionStatus.FAILED);
    }
}
