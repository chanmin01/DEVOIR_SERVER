package com.benly.global.recovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class StaleTaskPolicy {

    private final Duration scoringTimeout;
    private final Duration generationTimeout;

    public StaleTaskPolicy(@Value("${benly.recovery.scoring-timeout:5m}") Duration scoringTimeout,
                           @Value("${benly.recovery.generation-timeout:3m}") Duration generationTimeout) {
        this.scoringTimeout = scoringTimeout;
        this.generationTimeout = generationTimeout;
    }

    public LocalDateTime scoringThreshold() {
        return LocalDateTime.now().minus(scoringTimeout);
    }

    public LocalDateTime generationThreshold() {
        return LocalDateTime.now().minus(generationTimeout);
    }
}
