package com.benly.global.recovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaleTaskPolicyTest {

    @Test
    @DisplayName("타임아웃이 0이면 시작하지 않는다")
    void rejectZero() {
        assertThatThrownBy(() -> new StaleTaskPolicy(Duration.ZERO, Duration.ofMinutes(3), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("타임아웃이 음수면 시작하지 않는다")
    void rejectNegative() {
        assertThatThrownBy(() -> new StaleTaskPolicy(Duration.ofMinutes(10), Duration.ofMinutes(-1), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StaleTaskPolicy(Duration.ofMinutes(10), Duration.ofMinutes(3), Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기준 시각은 지금에서 타임아웃만큼 뺀 시각이다")
    void thresholds() {
        StaleTaskPolicy policy = new StaleTaskPolicy(
                Duration.ofMinutes(10), Duration.ofMinutes(3), Duration.ofMinutes(15));
        LocalDateTime before = LocalDateTime.now();

        LocalDateTime scoring = policy.scoringThreshold();
        LocalDateTime generation = policy.generationThreshold();
        LocalDateTime queue = policy.generationQueueThreshold();

        LocalDateTime after = LocalDateTime.now();
        assertThat(scoring).isBetween(before.minusMinutes(10), after.minusMinutes(10));
        assertThat(generation).isBetween(before.minusMinutes(3), after.minusMinutes(3));
        assertThat(queue).isBetween(before.minusMinutes(15), after.minusMinutes(15));
    }
}
