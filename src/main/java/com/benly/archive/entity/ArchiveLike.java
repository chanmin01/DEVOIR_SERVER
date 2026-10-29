package com.benly.archive.entity;

import com.benly.global.entity.BaseTimeEntity;
import com.benly.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "archive_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private ArchiveQuestion question;

    private ArchiveLike(User user, ArchiveQuestion question) {
        this.user = user;
        this.question = question;
    }

    public static ArchiveLike of(User user, ArchiveQuestion question) {
        return new ArchiveLike(user, question);
    }
}