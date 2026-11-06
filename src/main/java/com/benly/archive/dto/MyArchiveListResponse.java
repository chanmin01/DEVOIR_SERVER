package com.benly.archive.dto;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.ArchiveStatus;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.support.ArchiveTime;
import com.benly.session.entity.InterviewStage;

import java.util.List;
import java.util.Set;

public record MyArchiveListResponse(
        long totalQuestions,
        List<Item> archives,
        String nextCursor,
        boolean hasNext
) {

    public record Item(
            Long archiveId,
            CompanySummary company,
            InterviewStage stage,
            InterviewFormat format,
            String interviewedOn,
            String createdAt,
            ArchiveStatus status,
            List<Question> questions
    ) {
        public static Item of(InterviewArchive a, List<ArchiveQuestion> questions, Set<Long> likedIds) {
            return new Item(
                    a.getId(),
                    CompanySummary.from(a.getCompany()),
                    a.getStage(),
                    a.getFormat(),
                    a.getInterviewedMonth().toString(),
                    ArchiveTime.toKst(a.getCreatedAt()),
                    a.getStatus(),
                    questions.stream().map(q -> Question.of(q, likedIds.contains(q.getId()))).toList()
            );
        }
    }

    public record Question(Long questionId, String content, int likeCount, boolean liked) {
        static Question of(ArchiveQuestion q, boolean liked) {
            return new Question(q.getId(), q.getContent(), q.getLikeCount(), liked);
        }
    }

    public static MyArchiveListResponse empty(long totalQuestions) {
        return new MyArchiveListResponse(totalQuestions, List.of(), null, false);
    }
}
