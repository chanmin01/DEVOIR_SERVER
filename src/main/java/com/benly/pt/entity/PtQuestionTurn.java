package com.benly.pt.entity;

import com.benly.question.entity.Question;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "pt_question_turns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pt_turn_ordinal",
                        columnNames = {"session_id", "ordinal"}),
                @UniqueConstraint(name = "uk_pt_turn_question", columnNames = "question_id"),
                @UniqueConstraint(
                        name = "uk_pt_turn_request",
                        columnNames = {"session_id", "answer_request_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PtQuestionTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PtSession ptSession;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "answer_request_id", length = 36)
    private String answerRequestId;

    @Column(name = "answer_payload_hash", length = 64)
    private String answerPayloadHash;

    public static PtQuestionTurn create(PtSession detail, Question question, int ordinal) {
        PtQuestionTurn turn = new PtQuestionTurn();
        turn.ptSession = detail;
        turn.question = question;
        turn.ordinal = ordinal;
        return turn;
    }

    public int mainNumber() {
        return (ordinal - 1) / 3 + 1;
    }

    public int followUpNumber() {
        return (ordinal - 1) % 3;
    }

    public boolean answered() {
        return answerRequestId != null;
    }

    public void recordAnswer(UUID requestId, String payloadHash) {
        answerRequestId = requestId.toString();
        answerPayloadHash = payloadHash;
    }
}