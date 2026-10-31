package com.benly.archive.dto;

import com.benly.archive.entity.Company;

public record CompanySummary(Long companyId, String name) {

    public static CompanySummary from(Company company) {
        return new CompanySummary(company.getId(), company.getName());
    }
}
