package com.benly.feedback.service;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.dto.ScoredTopic;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.entity.Feedback;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.feedback.repository.FeedbackRepository;
import com.benly.feedback.repository.SessionFeedbackRepository;
import com.benly.feedback.service.CardScoringTx.TopicPlan;
import com.benly.question.entity.Question;
import com.benly.question.entity.QuestionSourceType;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import com.benly.session.repository.SessionRepository;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 카드 채점의 동시성 확인. 실제 DB 트랜잭션과 행 잠금을 쓰고 Claude 호출만 막는다.
 */
@SpringBootTest
class CardScoringConcurrencyTest {

    @Autowired
    private CardScoringService cardScoringService;
    @Autowired
    private CardScoringTx cardScoringTx;
    @Autowired
    private FeedbackScoringTx feedbackScoringTx;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private SessionFeedbackRepository sessionFeedbackRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CardScorer scorer;

    /** 이 테스트에서 만든 세션 채점 기록. 커밋된 채로 남으면 다른 통합 테스트의 전체 갱신 쿼리에 잡힌다 */
    private final ConcurrentLinkedQueue<Long> createdSessionFeedbacks = new ConcurrentLinkedQueue<>();

    @AfterEach
    void cleanUp() {
        createdSessionFeedbacks.forEach(id -> sessionFeedbackRepository.findById(id).ifPresent(sf -> {
            if (sf.isScoring()) {
                sf.fail();
                sessionFeedbackRepository.save(sf);
            }
        }));
        createdSessionFeedbacks.clear();
    }

    private static final FeedbackContent CONTENT =
            new FeedbackContent("잘함", "아쉬움", "다음", "약한 축", null, List.of());

    private Session session() {
        User user = userRepository.save(User.of("card-conc-" + System.nanoTime(), "테스터"));
        Session session = Session.create(user, "SERVICE", "TECHNICAL", "백엔드", "카카오");
        ReflectionTestUtils.setField(session, "status", SessionStatus.ANALYZING);
        return sessionRepository.save(session);
    }

    private Question main(Session session, int seq) {
        return questionRepository.save(
                Question.createMain(session, seq, "메인 질문 " + seq, QuestionSourceType.CLAUDE));
    }

    private ScoredTopic scored(Long mainId, int score) {
        return new ScoredTopic(mainId, new MainQuestionScore("제목", Map.of(), CONTENT), score, 0);
    }

    private Feedback reload(Feedback card) {
        return feedbackRepository.findById(card.getId()).orElseThrow();
    }

    private void runTogether(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        for (Runnable task : tasks) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("실패한 카드를 여러 작업이 동시에 다시 잡아도 Claude 채점은 한 번만 한다")
    void restartFailedCardOnlyOnce() throws InterruptedException {
        Session session = session();
        Question main = main(session, 1);
        Feedback card = Feedback.scoringQa(main);
        card.fail();
        feedbackRepository.save(card);

        AtomicInteger calls = new AtomicInteger();
        given(scorer.scoreTopic(any(), any(), any(), any())).willAnswer(inv -> {
            calls.incrementAndGet();
            Thread.sleep(300);
            return scored(main.getId(), 80);
        });

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> cardScoringService.scoreTopic(main.getId()));
        }
        runTogether(tasks);

        assertThat(calls.get()).isEqualTo(1);
        Feedback reloaded = reload(card);
        assertThat(reloaded.getScoringStatus()).isEqualTo(FeedbackStatus.COMPLETED);
        assertThat(reloaded.getTopicScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("오래 멈춘 이전 시도는 새 시도가 끝낸 카드를 실패로 바꾸거나 덮어쓰지 못한다")
    void oldAttemptCannotOverwrite() {
        Session session = session();
        Question main = main(session, 1);
        Feedback card = Feedback.scoringQa(main);
        LocalDateTime oldAttempt = LocalDateTime.now().minusMinutes(30).truncatedTo(ChronoUnit.MICROS);
        ReflectionTestUtils.setField(card, "scoringStartedAt", oldAttempt);
        feedbackRepository.save(card);

        TopicPlan newPlan = cardScoringTx.claimTopic(main.getId()).orElseThrow();
        assertThat(newPlan.attempt()).isNotEqualTo(oldAttempt);
        assertThat(cardScoringTx.completeTopic(card.getId(), newPlan.attempt(), scored(main.getId(), 90)))
                .isTrue();

        cardScoringTx.failCard(card.getId(), oldAttempt);
        boolean oldSaved = cardScoringTx.completeTopic(card.getId(), oldAttempt, scored(main.getId(), 10));

        assertThat(oldSaved).isFalse();
        Feedback reloaded = reload(card);
        assertThat(reloaded.getScoringStatus()).isEqualTo(FeedbackStatus.COMPLETED);
        assertThat(reloaded.getTopicScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("카드 여러 장이 동시에 끝나도 실패한 세션 채점은 한 번만 다시 열린다")
    void reopenSessionOnlyOnce() throws InterruptedException {
        Session session = session();
        Question first = main(session, 1);
        Question second = main(session, 2);
        feedbackRepository.save(Feedback.qa(first, "{}", 80));
        feedbackRepository.save(Feedback.qa(second, "{}", 70));
        SessionFeedback sf = SessionFeedback.startScoring(session);
        sf.fail();
        createdSessionFeedbacks.add(sessionFeedbackRepository.save(sf).getId());

        List<Optional<LocalDateTime>> results = new CopyOnWriteArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> results.add(feedbackScoringTx.reopenIfAllCardsDone(session.getId())));
        }
        runTogether(tasks);

        assertThat(results).hasSize(5);
        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
        SessionFeedback reloaded = sessionFeedbackRepository.findById(sf.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FeedbackStatus.SCORING);
    }

    @Test
    @DisplayName("아직 끝나지 않은 카드가 있으면 실패한 세션 채점을 다시 열지 않는다")
    void noReopenWithUnfinishedCard() {
        Session session = session();
        Question first = main(session, 1);
        main(session, 2);
        feedbackRepository.save(Feedback.qa(first, "{}", 80));
        SessionFeedback sf = SessionFeedback.startScoring(session);
        sf.fail();
        createdSessionFeedbacks.add(sessionFeedbackRepository.save(sf).getId());

        assertThat(feedbackScoringTx.reopenIfAllCardsDone(session.getId())).isEmpty();
    }
}
