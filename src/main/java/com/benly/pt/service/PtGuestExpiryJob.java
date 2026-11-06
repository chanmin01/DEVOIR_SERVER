package com.benly.pt.service;

import com.benly.pt.repository.PtSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
public class PtGuestExpiryJob {

    private final PtSessionRepository details;
    private final PtGuestExpiryTx tx;
    private final Clock clock;

    public PtGuestExpiryJob(
            PtSessionRepository details,
            PtGuestExpiryTx tx,
            @Qualifier("ptClock") Clock clock
    ) {
        this.details = details;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelay = 60000,
            initialDelay = 60000
    )
    public void expireGuests() {
        for (Long id : details.findExpiredGuestIds(
                clock.instant(),
                PageRequest.of(0, 100)
        )) {
            try {
                tx.expire(id);
            } catch (RuntimeException exception) {
                log.error(
                        "게스트 PT 만료 정리 실패: sessionId={}",
                        id,
                        exception
                );
            }
        }
    }
}