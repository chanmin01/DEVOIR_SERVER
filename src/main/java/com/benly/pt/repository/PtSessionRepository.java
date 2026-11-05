package com.benly.pt.repository;

import com.benly.pt.entity.PtSession;
import org.springframework.data.jpa.repository.JpaRepository;

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
}