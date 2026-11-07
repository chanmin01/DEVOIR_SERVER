package com.benly.feedback.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Table(name = "feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    // 발표 카드는 질문이 없어서 null
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", unique = true)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    // 채점 중인 카드는 아직 null
    @Column(name = "content", columnDefinition = "JSON")
    private String content;

    @Column(name = "topic_score")
    private Integer topicScore;

    // 카드 단위 채점 상태. 면접 중 미리 채점한 카드는 최종 채점에서 건너뛴다
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_status", nullable = false, length = 20)
    private FeedbackStatus scoringStatus;

    // 채점 시작 시각. 카드 채점의 시도 번호로도 쓴다
    @Column(name = "scoring_started_at")
    private LocalDateTime scoringStartedAt;

    private Feedback(Session session, Question question, CardType cardType,
                     String content, Integer topicScore, FeedbackStatus scoringStatus) {
        this.session = session;
        this.question = question;
        this.cardType = cardType;
        this.content = content;
        this.topicScore = topicScore;
        this.scoringStatus = scoringStatus;
        this.scoringStartedAt = scoringStatus == FeedbackStatus.SCORING ? now() : null;
    }

    // ===== 채점이 끝난 카드를 바로 만들 때 (기존 일괄 채점 경로) =====

    public static Feedback qa(Question question, String content, Integer topicScore) {
        return new Feedback(question.getSession(), question, CardType.QA,
                content, topicScore, FeedbackStatus.COMPLETED);
    }

    public static Feedback presentation(Session session, String content, Integer topicScore) {
        return new Feedback(session, null, CardType.PRESENTATION,
                content, topicScore, FeedbackStatus.COMPLETED);
    }

    public static Feedback create(Question question, String content, Integer topicScore) {
        return qa(question, content, topicScore);
    }

    // ===== 채점을 시작하면서 빈 카드를 먼저 만들 때 (미리 채점 경로) =====

    public static Feedback scoringQa(Question question) {
        return new Feedback(question.getSession(), question, CardType.QA,
                null, null, FeedbackStatus.SCORING);
    }

    public static Feedback scoringPresentation(Session session) {
        return new Feedback(session, null, CardType.PRESENTATION,
                null, null, FeedbackStatus.SCORING);
    }

    // ===== 상태 전이 =====

    public void complete(String content, Integer topicScore) {
        this.content = content;
        this.topicScore = topicScore;
        this.scoringStatus = FeedbackStatus.COMPLETED;
    }

    public void fail() {
        this.scoringStatus = FeedbackStatus.FAILED;
    }

    /**
     * 실패했거나 오래 멈춘 카드를 다시 채점 상태로 되돌린다. 시작 시각이 새 시도 번호가 된다
     */
    public void restart() {
        this.content = null;
        this.topicScore = null;
        this.scoringStatus = FeedbackStatus.SCORING;
        this.scoringStartedAt = now();
    }

    // ===== 조회 =====

    public boolean isPresentation() {
        return cardType == CardType.PRESENTATION;
    }

    public boolean isCompleted() {
        return scoringStatus == FeedbackStatus.COMPLETED;
    }

    public boolean isScoring() {
        return scoringStatus == FeedbackStatus.SCORING;
    }

    /**
     * 채점 중인데 기준 시각보다 먼저 시작했으면 멈춘 것으로 본다
     */
    public boolean isStale(LocalDateTime threshold) {
        return isScoring() && scoringStartedAt != null && scoringStartedAt.isBefore(threshold);
    }

    /**
     * 다시 채점해야 하는 카드인지. 실패했거나 오래 멈춘 경우
     */
    public boolean needsRescore(LocalDateTime staleThreshold) {
        return scoringStatus == FeedbackStatus.FAILED || isStale(staleThreshold);
    }

    /**
     * 지금 진행 중인 채점이 이 시도인지. 늦게 끝난 이전 시도가 결과를 덮어쓰지 않게 한다
     */
    public boolean isAttempt(LocalDateTime attempt) {
        return isScoring() && attempt != null && attempt.equals(scoringStartedAt);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
