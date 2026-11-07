package com.benly.feedback.repository;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {

    Optional<SessionFeedback> findBySession_Id(Long sessionId);

    boolean existsBySession_Id(Long sessionId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE SessionFeedback sf
               SET sf.status = :to,
                   sf.scoringStartedAt = :now,
                   sf.totalScore = null,
                   sf.summary = null,
                   sf.keyCoaching = null,
                   sf.coachingAction = null
             WHERE sf.session.id = :sessionId AND sf.status = :from
            """)
    int restartScoring(@Param("sessionId") Long sessionId,
                       @Param("from") FeedbackStatus from,
                       @Param("to") FeedbackStatus to,
                       @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE SessionFeedback sf
               SET sf.status = com.benly.feedback.entity.FeedbackStatus.FAILED
             WHERE sf.status = com.benly.feedback.entity.FeedbackStatus.SCORING
               AND sf.scoringStartedAt < :threshold
            """)
    int failStaleScoring(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE SessionFeedback sf
               SET sf.scoringStartedAt = :now,
                   sf.totalScore = null,
                   sf.summary = null,
                   sf.keyCoaching = null,
                   sf.coachingAction = null
             WHERE sf.session.id = :sessionId
               AND sf.status = com.benly.feedback.entity.FeedbackStatus.SCORING
               AND sf.scoringStartedAt < :threshold
            """)
    int restartStaleScoring(@Param("sessionId") Long sessionId,
                            @Param("threshold") LocalDateTime threshold,
                            @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sf FROM SessionFeedback sf WHERE sf.session.id = :sessionId")
    Optional<SessionFeedback> findBySessionIdForUpdate(@Param("sessionId") Long sessionId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE SessionFeedback sf
               SET sf.status = com.benly.feedback.entity.FeedbackStatus.FAILED
             WHERE sf.session.id = :sessionId
               AND sf.status = com.benly.feedback.entity.FeedbackStatus.SCORING
               AND sf.scoringStartedAt = :attempt
            """)
    int failAttempt(@Param("sessionId") Long sessionId,
                    @Param("attempt") LocalDateTime attempt);
}
