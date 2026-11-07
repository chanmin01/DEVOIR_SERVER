package com.benly.archive.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class ArchiveReportPolicy {

    private final int hideThreshold;

    public ArchiveReportPolicy(@Value("${benly.archive.report-hide-threshold:3}") int hideThreshold) {
        Assert.isTrue(hideThreshold > 0, "benly.archive.report-hide-threshold 값은 0보다 커야 합니다");
        this.hideThreshold = hideThreshold;
    }

    public int hideThreshold() {
        return hideThreshold;
    }
}
