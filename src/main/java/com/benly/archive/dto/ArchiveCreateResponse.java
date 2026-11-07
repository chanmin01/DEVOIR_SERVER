package com.benly.archive.dto;

import java.util.List;

public record ArchiveCreateResponse(
        Long archiveId,
        Long companyId,
        String companyName,
        boolean companyCreated,
        List<Long> questionIds
) {
}
