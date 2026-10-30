package com.benly.archive.dto;

import com.benly.archive.entity.CompanySource;

public record CompanyCandidate(
        Long companyId,
        String name,
        CompanySource source,
        String matchedKey,
        String matchedAlias,
        Long questionCount
) {

    public CompanyCandidate(Long companyId, String name, CompanySource source, String matchedKey, Long questionCount) {
        this(companyId, name, source, matchedKey, null, questionCount);
    }

    public boolean isExactMatch(String key) {
        return matchedKey.equals(key);
    }
}
