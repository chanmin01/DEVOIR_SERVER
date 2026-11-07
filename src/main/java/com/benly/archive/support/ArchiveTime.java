package com.benly.archive.support;

import com.benly.global.common.KstTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

public final class ArchiveTime {

    public static final ZoneId KST = KstTime.KST;

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
}
