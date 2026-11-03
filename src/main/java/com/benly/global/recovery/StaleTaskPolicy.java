package com.benly.global.recovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class StaleTaskPolicy {

    private final Duration scoringTimeout;
    private final Duration generationTimeout;
    private final Duration generationQueueTimeout;

    public StaleTaskPolicy(@Value("${benly.recovery.scoring-timeout:10m}") Duration scoringTimeout,
                           @Value("${benly.recovery.generation-timeout:3m}") Duration generationTimeout,
                           @Value("${benly.recovery.generation-queue-timeout:15m}") Duration generationQueueTimeout) {
        requirePositive(scoringTimeout, "scoring-timeout");
        requirePositive(generationTimeout, "generation-timeout");
        requirePositive(generationQueueTimeout, "generation-queue-timeout");
        this.scoringTimeout = scoringTimeout;
        this.generationTimeout = generationTimeout;
        this.generationQueueTimeout = generationQueueTimeout;
    }

    private static void requirePositive(Duration timeout, String name) {
        Assert.isTrue(timeout != null && timeout.compareTo(Duration.ZERO) > 0,
                "benly.recovery." + name + " 값은 0보다 커야 합니다");
    }

    public LocalDateTime scoringThreshold() {
        return LocalDateTime.now().minus(scoringTimeout);
    }

    public LocalDateTime generationThreshold() {
        return LocalDateTime.now().minus(generationTimeout);
    }

    public LocalDateTime generationQueueThreshold() {
        return LocalDateTime.now().minus(generationQueueTimeout);
    }
}
