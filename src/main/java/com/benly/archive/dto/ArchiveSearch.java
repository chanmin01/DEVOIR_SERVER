package com.benly.archive.dto;

import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.support.ArchiveSort;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;

public record ArchiveSearch(
        Long companyId,
        CompanyType companyType,
        InterviewStage stage,
        InterviewFormat format,
        String jobRole,
        ArchiveSort sort,
        String cursor,
        Integer size
) {
}
