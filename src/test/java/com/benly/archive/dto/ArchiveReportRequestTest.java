package com.benly.archive.dto;

import com.benly.archive.entity.ReportReason;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveReportRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("ReportReason의 모든 값이 요청 검증을 통과한다")
    void allReasonsAccepted() {
        for (ReportReason reason : ReportReason.values()) {
            assertThat(validator.validate(new ArchiveReportRequest(reason.name())))
                    .as(reason.name())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("정의되지 않은 사유와 빈 값은 거부한다")
    void unknownRejected() {
        assertThat(validator.validate(new ArchiveReportRequest("WRONG"))).hasSize(1);
        assertThat(validator.validate(new ArchiveReportRequest(null))).hasSize(1);
    }
}
