package com.benly.archive.dto;

import com.benly.archive.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ArchiveReportRequest(
        @NotBlank(message = "신고 사유를 선택해 주세요.")
        @Pattern(regexp = "FALSE_INFO|AD|INAPPROPRIATE|COPIED|NDA_VIOLATION",
                message = "허용되지 않는 값입니다: reason")
        String reason
) {
    public ReportReason toReason() {
        return ReportReason.valueOf(reason);
    }
}
