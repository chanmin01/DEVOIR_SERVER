package com.benly.feedback.repository;

import com.benly.feedback.entity.CardType;
import com.benly.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Query("""
            SELECT f FROM Feedback f LEFT JOIN FETCH f.question q
            WHERE f.session.id = :sessionId
            ORDER BY q.seq ASC NULLS FIRST
            """)
    List<Feedback> findWithQuestionBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM Feedback f WHERE f.session.id = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);

    /** 질문 카드 찾기. question_id가 유니크라 카드는 최대 1장 */
    Optional<Feedback> findByQuestion_Id(Long questionId);

    /** 발표 카드 찾기. 세션당 1장 */
    Optional<Feedback> findBySession_IdAndCardType(Long sessionId, CardType cardType);
}
