package com.benly.archive.dto;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;

import java.util.List;
import java.util.Set;

public record ArchiveCard(
        Long archiveId,
        CompanySummary company,
        CompanyType companyType,
        InterviewStage stage,
        InterviewFormat format,
        String jobRole,
        String interviewedOn,
        String memo,
        int likeCount,
        boolean mine,
        boolean reported,
        List<Question> questions
) {

    public record Question(
            Long questionId,
            String content,
            int likeCount,
            boolean liked
    ) {
        static Question of(ArchiveQuestion q, Set<Long> likedIds) {
            return new Question(q.getId(), q.getContent(), q.getLikeCount(), likedIds.contains(q.getId()));
        }
    }

    public static ArchiveCard of(InterviewArchive a, List<ArchiveQuestion> questions,
                                 Set<Long> likedIds, boolean mine, boolean reported) {
        return new ArchiveCard(
                a.getId(),
                CompanySummary.from(a.getCompany()),
                a.getCompanyType(),
                a.getStage(),
                a.getFormat(),
                a.getJobRole(),
                a.getInterviewedMonth().toString(),
                a.getMemo(),
                a.getLikeCount(),
                mine,
                reported,
                questions.stream().map(q -> Question.of(q, likedIds)).toList()
        );
    }
}
