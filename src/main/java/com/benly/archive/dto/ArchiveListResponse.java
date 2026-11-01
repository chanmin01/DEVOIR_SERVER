package com.benly.archive.dto;

import java.util.List;

public record ArchiveListResponse(
        List<ArchiveCard> archives,
        String nextCursor,
        boolean hasNext
) {

    public static ArchiveListResponse empty() {
        return new ArchiveListResponse(List.of(), null, false);
    }
}
