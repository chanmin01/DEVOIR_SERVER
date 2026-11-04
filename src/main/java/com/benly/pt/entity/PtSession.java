package com.benly.pt.entity;

import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.policy.PtInterviewRules;
import com.benly.question.entity.AnswerType;

@Entity
@Table(
        name = "pt_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pt_creation_request",
                columnNames = "creation_request_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtSession {

    @Id
    @Column(name = "session_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "task_id",
            nullable = false,
            updatable = false
    )
    private PtTask task;

    @Column(
            name = "creation_request_id",
            nullable = false,
            length = 36
    )
    private String creationRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PtPhase phase;

    @Column(name = "preparation_minutes", nullable = false)
    private int preparationMinutes;

    @Column(name = "preparation_started_at", nullable = false)
    private Instant preparationStartedAt;

    @Column(name = "preparation_deadline", nullable = false)
    private Instant preparationDeadline;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "track", length = 20)
    private PtTrack track;

    @Column(name = "presentation_started_at")
    private Instant presentationStartedAt;

    @Column(name = "presentation_deadline")
    private Instant presentationDeadline;

    @Column(name = "presentation_transcript", columnDefinition = "MEDIUMTEXT")
    private String presentationTranscript;

    @Enumerated(EnumType.STRING)
    @Column(name = "presentation_input_type", length = 10)
    private AnswerType presentationInputType;

    @Column(name = "presentation_request_id", length = 36)
    private String presentationRequestId;

    @Column(name = "presentation_submitted_at")
    private Instant presentationSubmittedAt;

    public static PtSession prepare(
            Session session,
            PtTask task,
            UUID requestId,
            int minutes,
            Instant now,
            Instant expiresAt
    ) {
        PtSession detail = new PtSession();

        detail.session = session;
        detail.task = task;
        detail.creationRequestId = requestId.toString();
        detail.phase = PtPhase.PREPARING;
        detail.preparationMinutes = minutes;
        detail.preparationStartedAt = now;
        detail.preparationDeadline =
                now.plusSeconds(minutes * 60L);
        detail.expiresAt = expiresAt;

        return detail;
    }

    public void selectTrack(PtTrack track) {
        requirePhase(PtPhase.PREPARING);

        if (track == null) {
            throw new BusinessException(PtErrorCode.INVALID_REQUEST);
        }

        this.track = track;
    }

    public void startPresentation(Instant now) {
        if (phase == PtPhase.PRESENTING) {
            return;
        }

        requirePhase(PtPhase.PREPARING);

        if (track == null) {
            throw new BusinessException(PtErrorCode.TRACK_REQUIRED);
        }

        // 준비 마감 후 늦게 요청해도 발표 시간을 추가로 주지 않는다.
        this.presentationStartedAt = now.isBefore(preparationDeadline)
                ? now
                : preparationDeadline;

        this.presentationDeadline = presentationStartedAt
                .plusSeconds(PtInterviewRules.PRESENTATION_SECONDS);

        this.phase = PtPhase.PRESENTING;
    }

    public void submitTextPresentation(
            UUID requestId,
            String text,
            Instant now
    ) {
        if (presentationRequestId != null) {
            if (presentationRequestId.equals(requestId.toString())) {
                if (presentationInputType == AnswerType.TEXT
                        && presentationTranscript.equals(text)) {
                    return;
                }

                throw new BusinessException(PtErrorCode.REQUEST_CONFLICT);
            }

            throw new BusinessException(
                    PtErrorCode.PRESENTATION_ALREADY_SUBMITTED
            );
        }

        requirePhase(PtPhase.PRESENTING);

        this.presentationRequestId = requestId.toString();
        this.presentationTranscript = text;
        this.presentationInputType = AnswerType.TEXT;
        this.presentationSubmittedAt = now;
        this.phase = PtPhase.GENERATING;
    }

    public void cancel() {
        if (phase == PtPhase.COMPLETED) {
            throw new BusinessException(PtErrorCode.CANNOT_CANCEL);
        }

        this.phase = PtPhase.CANCELED;
    }

    private void requirePhase(PtPhase expected) {
        if (phase != expected) {
            throw new BusinessException(PtErrorCode.INVALID_PHASE);
        }
    }
}