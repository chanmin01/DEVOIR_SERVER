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

    // 직접 입력 회사는 볼 수 있는 질문이 있을 때만 자동완성에 띄운다
    public boolean isShowable() {
        return source == CompanySource.DART || questionCount > 0;
    }
}
