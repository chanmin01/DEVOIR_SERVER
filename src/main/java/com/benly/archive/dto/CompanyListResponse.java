package com.benly.archive.dto;

import java.time.YearMonth;
import java.util.List;

public record CompanyListResponse(
        List<Item> companies,
        String nextCursor,
        boolean hasNext
) {

    public record Item(
            Long companyId,
            String name,
            long questionCount,
            String latestInterviewedOn
    ) {
        public static Item from(CompanyQuestionCount row) {
            return new Item(
                    row.companyId(),
                    row.name(),
                    row.questionCount(),
                    YearMonth.from(row.latestInterviewedAt()).toString());
        }
    }
}
