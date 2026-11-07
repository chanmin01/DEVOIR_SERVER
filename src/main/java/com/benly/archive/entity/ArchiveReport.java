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
    @JoinColumn(name = "archive_id", nullable = false)
    private InterviewArchive archive;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReportReason reason;

    private ArchiveReport(User user, InterviewArchive archive, ReportReason reason) {
        this.user = user;
        this.archive = archive;
        this.reason = reason;
    }

    public static ArchiveReport of(User user, InterviewArchive archive, ReportReason reason) {
        return new ArchiveReport(user, archive, reason);
    }
}
