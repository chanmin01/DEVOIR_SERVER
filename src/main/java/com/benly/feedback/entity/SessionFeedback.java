package com.benly.feedback.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Column(name = "filler_word_count")
    private Integer fillerWordCount;

    @Column(name = "filler_word_note", length = 200)
    private String fillerWordNote;

    @Column(name = "speech_speed")
    private Integer speechSpeed;

    @Column(name = "speech_speed_note", length = 200)
    private String speechSpeedNote;

    private SessionFeedback(Session session) {
        this.session = session;
        this.status = FeedbackStatus.SCORING;
        this.scoringStartedAt = LocalDateTime.now();
    }

    public static SessionFeedback startScoring(Session session) {
        return new SessionFeedback(session);
    }

    public void resetForRescore() {
        this.totalScore = null;
        this.summary = null;
        this.keyCoaching = null;
        this.coachingAction = null;
        this.fillerWordCount = null;
        this.fillerWordNote = null;
        this.speechSpeed = null;
        this.speechSpeedNote = null;
        this.status = FeedbackStatus.SCORING;
        this.scoringStartedAt = LocalDateTime.now();
    }

    public void complete(Integer totalScore, String summary,
                         String keyCoaching, String coachingAction) {
        this.totalScore = totalScore;
        this.summary = summary;
        this.keyCoaching = keyCoaching;
        this.coachingAction = coachingAction;
        this.status = FeedbackStatus.COMPLETED;
    }

    /**
     * TODO(후속 스프린트): 현재 미사용. Answer.durationSec이 침묵 포함이라 속도 계산 부정확.
     *   OpenAI Whisper word 타임스탬프로 순수 발화시간 계산 후 이 메서드로 반영 고려
     */
    public void applyDelivery(Integer speechSpeed, String speechSpeedNote,
                              Integer fillerWordCount, String fillerWordNote) {
        this.speechSpeed = speechSpeed;
        this.speechSpeedNote = speechSpeedNote;
        this.fillerWordCount = fillerWordCount;
        this.fillerWordNote = fillerWordNote;
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
}
