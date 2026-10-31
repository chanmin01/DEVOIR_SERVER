package com.benly.archive.support;

import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.global.exception.BusinessException;

public record ArchiveCursor(Long score, Long id) {

    public static ArchiveCursor ofId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new ArchiveCursor(null, parsePositive(raw));
    }

    public static ArchiveCursor ofScoreAndId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("_");
        if (parts.length != 2) {
            throw invalid();
        }
        long score = parseNonNegative(parts[0]);
        long id = parsePositive(parts[1]);
        return new ArchiveCursor(score, id);
    }

    public static String encode(long id) {
        return String.valueOf(id);
    }

    public static String encode(long score, long id) {
        return score + "_" + id;
    }

    private static long parsePositive(String value) {
        long parsed = parseNonNegative(value);
        if (parsed == 0) {
            throw invalid();
        }
        return parsed;
    }

    private static long parseNonNegative(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ArchiveErrorCode.INVALID_CURSOR);
    }
}
