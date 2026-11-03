package com.benly.pt.entity;

import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
}