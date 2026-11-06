package com.benly.archive.dto;

import com.benly.archive.entity.ArchiveLike;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;

import java.util.List;

public record LikedQuestionListResponse(
        long totalCount,
        List<Item> questions,
        String nextCursor,
        boolean hasNext
) {

    public record Item(
            Long questionId,
            Long archiveId,
            CompanySummary company,
            CompanyType companyType,
            InterviewStage stage,
            InterviewFormat format,
            String jobRole,
            String interviewedOn,
            String content,
            int likeCount,
            boolean liked,
            boolean mine
    ) {
        public static Item of(ArchiveLike like, Long userId) {
            ArchiveQuestion q = like.getQuestion();
            InterviewArchive a = q.getArchive();
            return new Item(
                    q.getId(),
                    a.getId(),
                    CompanySummary.from(a.getCompany()),
                    a.getCompanyType(),
                    a.getStage(),
                    a.getFormat(),
                    a.getJobRole(),
                    a.getInterviewedMonth().toString(),
                    q.getContent(),
                    q.getLikeCount(),
                    true,
                    a.isOwnedBy(userId)
            );
        }
    }

    public static LikedQuestionListResponse empty(long totalCount) {
        return new LikedQuestionListResponse(totalCount, List.of(), null, false);
    }
}
