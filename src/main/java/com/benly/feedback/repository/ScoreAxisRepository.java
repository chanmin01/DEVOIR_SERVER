package com.benly.feedback.repository;

import com.benly.feedback.entity.ScoreAxis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScoreAxisRepository extends JpaRepository<ScoreAxis, Long> {

    @Query("SELECT sa FROM ScoreAxis sa JOIN FETCH sa.feedback f WHERE f.session.id = :sessionId")
    List<ScoreAxis> findBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("DELETE FROM ScoreAxis sa WHERE sa.feedback IN " +
            "(SELECT f FROM Feedback f WHERE f.session.id = :sessionId)")
    void deleteBySessionId(@Param("sessionId") Long sessionId);

    /** 카드 하나를 다시 채점할 때 이전 축 점수를 지운다 */
    @Modifying
    @Query("DELETE FROM ScoreAxis sa WHERE sa.feedback.id = :feedbackId")
    void deleteByFeedbackId(@Param("feedbackId") Long feedbackId);
}
