package com.benly.archive.entity;

import com.benly.global.entity.BaseTimeEntity;
import com.benly.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "archive_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ArchiveQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReportReason reason;

    private ArchiveReport(User user, ArchiveQuestion question, ReportReason reason) {
        this.user = user;
        this.question = question;
        this.reason = reason;
    }

    public static ArchiveReport of(User user, ArchiveQuestion question, ReportReason reason) {
        return new ArchiveReport(user, question, reason);
    }
}