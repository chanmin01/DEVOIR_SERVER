package com.benly.archive.dto;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.ArchiveStatus;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.session.entity.InterviewStage;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MyArchiveListResponse(
        long totalQuestions,
        List<Item> archives,
        String nextCursor,
        boolean hasNext
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public record Item(
            Long archiveId,
            CompanySummary company,
            InterviewStage stage,
            InterviewFormat format,
            String interviewedOn,
            OffsetDateTime createdAt,
            ArchiveStatus status,
            List<Question> questions
    ) {
        public static Item of(InterviewArchive a, List<ArchiveQuestion> questions) {
            return new Item(
                    a.getId(),
                    CompanySummary.from(a.getCompany()),
                    a.getStage(),
                    a.getFormat(),
                    a.getInterviewedMonth().toString(),
                    a.getCreatedAt().atZone(KST).toOffsetDateTime(),
                    a.getStatus(),
                    questions.stream().map(Question::of).toList()
            );
        }
    }

    public record Question(Long questionId, String content, int likeCount) {
        static Question of(ArchiveQuestion q) {
            return new Question(q.getId(), q.getContent(), q.getLikeCount());
        }
    }

    public static MyArchiveListResponse empty(long totalQuestions) {
        return new MyArchiveListResponse(totalQuestions, List.of(), null, false);
    }
}
