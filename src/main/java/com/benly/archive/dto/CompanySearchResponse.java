package com.benly.archive.dto;

import com.benly.archive.entity.CompanySource;

import java.util.List;

public record CompanySearchResponse(List<Item> companies) {

    public record Item(
            Long companyId,
            String name,
            String matchedAlias,
            CompanySource source,
            long questionCount
    ) {
        public static Item from(CompanyCandidate c) {
            return new Item(c.companyId(), c.name(), c.matchedAlias(), c.source(), c.questionCount());
        }
    }
}
