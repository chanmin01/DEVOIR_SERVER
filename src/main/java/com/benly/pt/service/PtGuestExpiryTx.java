package com.benly.pt.service;

import com.benly.pt.entity.PtPhase;
import com.benly.pt.entity.PtSession;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.repository.SessionRepository;
import com.benly.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PtGuestExpiryTx {
    private final PtSessionRepository details;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;

    public PtGuestExpiryTx(
            PtSessionRepository details,
            SessionRepository sessions,
            UserRepository users,
            @Qualifier("ptClock") Clock clock) {
        this.details = details;
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public boolean expire(Long sessionId) {
        Long ownerId = details.findOwnerId(sessionId).orElse(null);
        if (ownerId == null) return false;
        var owner = users.findByIdForUpdate(ownerId).orElse(null);
        if (owner == null || !owner.isGuest()) return false;
        var parent = sessions.findOwnedSessionForUpdate(sessionId, ownerId).orElse(null);
        if (parent == null) return false;
        PtSession detail = details.findById(sessionId).orElseThrow();
        if (detail.getExpiresAt() == null
                || detail.getExpiresAt().isAfter(clock.instant())
                || detail.getRetentionClosedAt() != null) return false;
        if (detail.getPhase() != PtPhase.COMPLETED) {
            detail.cancel();
            parent.markCanceled();
        }
        detail.closeRetention(clock.instant());
        return true;
    }
}