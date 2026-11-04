package com.benly.pt.repository;

import com.benly.pt.entity.PtSession;
import com.benly.session.entity.Session;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PtSessionRepository
        extends JpaRepository<PtSession, Long> {

    Optional<PtSession> findByCreationRequestId(
            String requestId
    );

    Optional<PtSession> findByIdAndSession_User_IdAndSession_DeletedAtIsNull(
            Long id,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Session s
            where s.id = :id
              and s.user.id = :userId
              and s.deletedAt is null
            """)
    Optional<Session> findOwnedSessionForUpdate(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}