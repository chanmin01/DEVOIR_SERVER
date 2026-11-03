package com.benly.archive.dto;

public record ArchiveLikeResponse(
        Long questionId,
        boolean liked,
        int likeCount
) {
}
