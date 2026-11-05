package com.benly.pt.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "pt_audio_receipts",
        uniqueConstraints =
        @UniqueConstraint(
                name = "uk_pt_audio_request",
                columnNames = {"session_id", "request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtAudioReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PtSession ptSession;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    public static PtAudioReceipt create(
            PtSession detail,
            UUID requestId,
            Long questionId,
            String payloadHash,
            Instant admittedAt) {
        PtAudioReceipt receipt = new PtAudioReceipt();
        receipt.ptSession = detail;
        receipt.requestId = requestId.toString();
        receipt.questionId = questionId;
        receipt.payloadHash = payloadHash;
        receipt.admittedAt = admittedAt;
        return receipt;
    }
}