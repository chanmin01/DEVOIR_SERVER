package com.benly.archive.dto;

import java.time.LocalDate;

public record CompanyQuestionCount(
        Long companyId,
        String name,
        Long questionCount,
        LocalDate latestInterviewedAt
) {
}
