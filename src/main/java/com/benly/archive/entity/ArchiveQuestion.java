package com.benly.archive.entity;

import com.benly.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "archive_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveQuestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archive_id", nullable = false)
    private InterviewArchive archive;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    private ArchiveQuestion(InterviewArchive archive, int seq, String content) {
        this.archive = archive;
        this.seq = seq;
        this.content = content;
        this.likeCount = 0;
    }

    public static ArchiveQuestion of(InterviewArchive archive, int seq, String content) {
        return new ArchiveQuestion(archive, seq, content);
    }
}
