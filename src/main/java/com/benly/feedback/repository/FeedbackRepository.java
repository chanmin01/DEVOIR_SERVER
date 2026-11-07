package com.benly.feedback.repository;

import com.benly.feedback.entity.CardType;
import com.benly.feedback.entity.Feedback;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 질문 카드 찾기. question_id가 유니크라 카드는 최대 1장
     */
    Optional<Feedback> findByQuestion_Id(Long questionId);

    /**
     * 발표 카드 찾기. 세션당 1장
     */
    Optional<Feedback> findBySession_IdAndCardType(Long sessionId, CardType cardType);

    /**
     * 질문 카드를 잠그고 찾는다. 재시작 판단과 변경을 한 작업만 하게 한다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Feedback f WHERE f.question.id = :questionId")
    Optional<Feedback> findByQuestionIdForUpdate(@Param("questionId") Long questionId);

    /**
     * 카드를 잠그고 찾는다. 저장이나 실패 처리 전에 시도 번호를 확인할 때 쓴다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Feedback f WHERE f.id = :id")
    Optional<Feedback> findByIdForUpdate(@Param("id") Long id);
}
