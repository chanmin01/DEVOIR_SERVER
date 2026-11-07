package com.benly.global.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * DB에 저장된 시각을 응답용 KST 문자열로 바꾼다.
 * DB 값은 서버 기본 시간대 기준이므로, 운영 서버가 UTC여도 KST로 변환된다.
 * 형식 예: 2026-11-07T12:05:01+09:00
 */
public final class KstTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private KstTime() {
    }

    public static String format(LocalDateTime serverTime) {
        return format(serverTime, ZoneId.systemDefault());
    }

    /** 테스트에서 서버 시간대를 바꿔 검증할 수 있게 열어 둔다 */
    static String format(LocalDateTime serverTime, ZoneId serverZone) {
        if (serverTime == null) {
            return null;
        }
        return serverTime.atZone(serverZone)
                .withZoneSameInstant(KST)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(FORMAT);
    }
}
