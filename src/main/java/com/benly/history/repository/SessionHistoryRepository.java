package com.benly.history.repository;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.session.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionHistoryRepository extends JpaRepository<Session, Long> {

    @Query("""
            SELECT sf FROM SessionFeedback sf
            JOIN FETCH sf.session s
            WHERE s.user.id = :userId AND sf.status = :status
            ORDER BY s.createdAt DESC
            """)
    List<SessionFeedback> findScoredSessions(@Param("userId") Long userId,
                                             @Param("status") FeedbackStatus status);

    @Query("""
            SELECT sf FROM SessionFeedback sf
            JOIN FETCH sf.session s
            WHERE s.user.id = :userId AND s.companyType = :companyType AND sf.status = :status
            ORDER BY s.createdAt DESC
            """)
    List<SessionFeedback> findScoredSessionsByCompany(@Param("userId") Long userId,
                                                      @Param("companyType") String companyType,
                                                      @Param("status") FeedbackStatus status);

    // ↓ count 쿼리 2개는 기존 그대로
    @Query("""
            SELECT COUNT(s) FROM Session s
            JOIN SessionFeedback sf ON sf.session = s
            WHERE s.user.id = :userId AND sf.status = :status
            """)
    long countScored(@Param("userId") Long userId,
                     @Param("status") FeedbackStatus status);

    @Query("""
            SELECT COUNT(s) FROM Session s
            JOIN SessionFeedback sf ON sf.session = s
            WHERE s.user.id = :userId AND sf.status = :status AND s.createdAt >= :from
            """)
    long countScoredSince(@Param("userId") Long userId,
                          @Param("status") FeedbackStatus status,
                          @Param("from") LocalDateTime from);
}