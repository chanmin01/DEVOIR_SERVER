package com.benly.pt.service;

import com.benly.pt.repository.PtSessionRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class PtGuestExpiryJob {
    private final PtSessionRepository details;
    private final PtGuestExpiryTx tx;
    private final Clock clock;

    public PtGuestExpiryJob(
            PtSessionRepository details, PtGuestExpiryTx tx, @Qualifier("ptClock") Clock clock) {
        this.details = details;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void expireGuests() {
        for (Long id : details.findExpiredGuestIds(clock.instant(), PageRequest.of(0, 100))) {
            tx.expire(id);
        }
    }
}