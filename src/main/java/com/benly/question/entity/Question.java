package com.benly.question.entity;

import com.benly.global.entity.BaseTimeEntity;
import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Question parent;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 꼬리질문일 때만 채워진다. 메인은 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", length = 20)
    private FollowUpStrategy strategy;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "short_title", columnDefinition = "TEXT")
    private String shortTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private QuestionSourceType sourceType; //CLAUDE / SEED


    private Question(Session session, Question parent, String type, FollowUpStrategy strategy,
                     Integer seq, String content, String shortTitle, QuestionSourceType sourceType) {
        this.session = session;
        this.parent = parent;
        this.type = type;
        this.strategy = strategy;
        this.seq = seq;
        this.content = content;
        this.shortTitle = shortTitle;
        this.sourceType = sourceType;
    }

    public static Question createMain(Session session, Integer seq, String content,
                                      QuestionSourceType sourceType) {
        return new Question(session, null, "MAIN", null, seq, content, null, sourceType);
    }

    public static Question createFollowUp(Session session, Question parent, Integer seq,
                                          String content, FollowUpStrategy strategy,
                                          QuestionSourceType sourceType) {
        return new Question(session, parent, "FOLLOW_UP", strategy, seq, content, null, sourceType);
    }

    /** 채점 시 카드 제목(요약)을 채운다. */
    public void assignShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public boolean isMain() {
        return this.parent == null;
    }
}