package com.benly.feedback.repository;

import com.benly.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
