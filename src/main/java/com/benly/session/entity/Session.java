package com.benly.session.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "company_type", nullable = false, length = 20)
    private String companyType;

    @Column(name = "stage", nullable = false, length = 20)
    private String stage;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "generation_started_at")
    private LocalDateTime generationStartedAt;

    private Session(User user, String companyType, String stage, String jobTitle, String companyName, SessionStatus status) {
        this.user = user;
        this.companyType = companyType;
        this.stage = stage;
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.status = status;
    }

    public static Session create(User user, String companyType, String stage, String jobTitle, String companyName) {
        return new Session(user, companyType, stage, jobTitle, companyName, SessionStatus.GENERATING);
    }

    public boolean isGenerationStale(LocalDateTime startedThreshold, LocalDateTime queuedThreshold) {
        if (status != SessionStatus.GENERATING) {
            return false;
        }
        return generationStartedAt != null
                ? generationStartedAt.isBefore(startedThreshold)
                : getCreatedAt().isBefore(queuedThreshold);
    }

    public void markReady(){
        this.status = SessionStatus.READY;
    }

    public void markFailed() {
        this.status = SessionStatus.FAILED;
    }

    public void markInProgress() {
        this.status = SessionStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = SessionStatus.COMPLETED;
    }

    public void markAnalyzing() {
        this.status = SessionStatus.ANALYZING;
    }

    public void markCanceled() {
        this.status = SessionStatus.CANCELED;
    }
}
