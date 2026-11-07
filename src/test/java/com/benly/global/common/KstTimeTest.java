package com.benly.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstTimeTest {

    @Test
    @DisplayName("운영 서버처럼 UTC로 저장된 시각은 9시간 더해 KST로 바꾼다")
    void utcServerToKst() {
        LocalDateTime utc = LocalDateTime.of(2026, 11, 7, 3, 5, 1, 997_258_000);

        String result = KstTime.format(utc, ZoneOffset.UTC);

        assertThat(result).isEqualTo("2026-11-07T12:05:01+09:00");
    }

    @Test
    @DisplayName("로컬처럼 KST로 저장된 시각은 그대로 두고 오프셋만 붙인다")
    void kstServerKeepsTime() {
        LocalDateTime kst = LocalDateTime.of(2026, 11, 7, 12, 5, 1);

        String result = KstTime.format(kst, ZoneId.of("Asia/Seoul"));

        assertThat(result).isEqualTo("2026-11-07T12:05:01+09:00");
    }

    @Test
    @DisplayName("자정을 넘기면 날짜도 KST 기준으로 바뀐다")
    void dateRollsOver() {
        LocalDateTime utc = LocalDateTime.of(2026, 11, 7, 16, 30, 0);

        String result = KstTime.format(utc, ZoneOffset.UTC);

        assertThat(result).isEqualTo("2026-11-08T01:30:00+09:00");
    }

    @Test
    @DisplayName("null이면 null을 돌려준다")
    void nullSafe() {
        assertThat(KstTime.format(null)).isNull();
    }
}
