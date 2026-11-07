package com.benly.feedback.entity;

import com.benly.global.entity.BaseEntity;
import com.benly.question.entity.Question;
import com.benly.session.entity.Session;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    // 발표 카드는 질문이 없어서 null
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", unique = true)
    private Question question;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "content", columnDefinition = "JSON")
    private String content;

    @Column(name = "topic_score")
    private Integer topicScore;

    private Feedback(Session session, Question question, CardType cardType, String content, Integer topicScore) {
        this.session = session;
        this.question = question;
        this.cardType = cardType;
        this.content = content;
        this.topicScore = topicScore;
    }

    public static Feedback qa(Question question, String content, Integer topicScore) {
        return new Feedback(question.getSession(), question, CardType.QA, content, topicScore);
    }

    public static Feedback presentation(Session session, String content, Integer topicScore) {
        return new Feedback(session, null, CardType.PRESENTATION, content, topicScore);
    }

    public static Feedback create(Question question, String content, Integer topicScore) {
        return qa(question, content, topicScore);
    }

    public boolean isPresentation() {
        return cardType == CardType.PRESENTATION;
    }
}
