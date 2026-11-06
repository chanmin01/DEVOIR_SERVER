package com.benly.archive.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class ArchiveTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private ArchiveTime() {
    }

    public static LocalDateTime startOfTodayKst() {
        return LocalDate.now(KST)
                .atStartOfDay(KST)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    public static YearMonth currentMonthKst() {
        return YearMonth.now(KST);
    }

    public static String toKst(LocalDateTime dbTime) {
        if (dbTime == null) {
            return null;
        }
        return dbTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(KST)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(KST_FORMAT);
    }
}
