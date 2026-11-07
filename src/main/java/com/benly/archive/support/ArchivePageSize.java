package com.benly.archive.support;

import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.global.exception.BusinessException;

public final class ArchivePageSize {

    public static final int DEFAULT = 20;
    public static final int MAX = 50;

    private ArchivePageSize() {
    }

    public static int resolve(Integer size) {
        if (size == null) {
            return DEFAULT;
        }
        if (size < 1 || size > MAX) {
            throw new BusinessException(ArchiveErrorCode.INVALID_PAGE_SIZE);
        }
        return size;
    }
}
