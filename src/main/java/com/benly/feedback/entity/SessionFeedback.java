package com.benly.feedback.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Table(name = "session_feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionFeedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private Session session;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "key_coaching", columnDefinition = "TEXT")
    private String keyCoaching;

    @Column(name = "coaching_action", columnDefinition = "TEXT")
    private String coachingAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_status", nullable = false, length = 20)
    private FeedbackStatus status;

    @Column(name = "scoring_started_at")
    private LocalDateTime scoringStartedAt;

    private SessionFeedback(Session session) {
        this.session = session;
        this.status = FeedbackStatus.SCORING;
        this.scoringStartedAt = newAttemptTime();
    }

    public static SessionFeedback startScoring(Session session) {
        return new SessionFeedback(session);
    }

    public void resetForRescore() {
        this.totalScore = null;
        this.summary = null;
        this.keyCoaching = null;
        this.coachingAction = null;
        this.status = FeedbackStatus.SCORING;
        this.scoringStartedAt = newAttemptTime();
    }

    public void complete(Integer totalScore, String summary,
                         String keyCoaching, String coachingAction) {
        this.totalScore = totalScore;
        this.summary = summary;
        this.keyCoaching = keyCoaching;
        this.coachingAction = coachingAction;
        this.status = FeedbackStatus.COMPLETED;
    }

    public void fail() {
        this.status = FeedbackStatus.FAILED;
    }

    public boolean isCompleted() {
        return this.status == FeedbackStatus.COMPLETED;
    }

    public boolean isScoring() {
        return this.status == FeedbackStatus.SCORING;
    }

    public boolean isFailed() {
        return this.status == FeedbackStatus.FAILED;
    }

    public boolean isStale(LocalDateTime threshold) {
        return isScoring() && scoringStartedAt != null && scoringStartedAt.isBefore(threshold);
    }

    public static LocalDateTime newAttemptTime() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isCurrentAttempt(LocalDateTime attempt) {
        return isScoring() && attempt != null && attempt.equals(scoringStartedAt);
    }
}
