package com.benly.feedback.dto;

import java.util.List;

/**
 * 리포트 조회(GET /sessions/{id}/feedback) 응답. 노션 명세 기준.
 */
public record FeedbackReportResponse(
        Long sessionId,
        Meta meta,
        KeyCoaching keyCoaching,
        Integer totalScore,
        ScoreBreakdown scoreBreakdown,
        String verdict,
        Radar radar,
        List<Card> cards
) {

    public record Meta(
            String format,
            String companyType,
            String stage,
            String companyName,
            String jobTitle,
            String createdAt
    ) {}

    public record KeyCoaching(
            String weakness,
            String action
    ) {}

    // PT만. QA는 null
    public record ScoreBreakdown(
            int presentation,
            int qaAverage,
            int presentationWeight,
            int qaWeight
    ) {}

    public record Radar(
            String type,
            List<Axis> axes
    ) {
        public record Axis(
                String axis,
                String label,
                Integer score
        ) {}
    }

    public record Card(
            String cardType,
            Long questionId,
            Integer num,
            String shortTitle,
            Integer topicScore,
            String question,
            String answer,
            boolean skipped,
            Integer durationSeconds,          // PT 발표 카드만
            String track,                     // PT 발표 카드만
            List<Requirement> requirements,   // PT 발표 카드만, 그 외 빈 배열
            String good,
            String weak,
            String next,
            String weakAxis,
            ImprovedAnswer improvedAnswer,    // 질문 카드만
            ImprovedOutline improvedOutline,  // PT 발표 카드만
            List<Tail> tails
    ) {}

    public record Tail(
            String strategy,
            String question,
            String answer,
            boolean skipped,
            String good,
            String weak,
            String next,
            String weakAxis,
            String improvedAfter
    ) {}

    public record ImprovedAnswer(String before, String after) {}

    public record ImprovedOutline(List<String> before, List<String> after) {}

    public record Requirement(String requirement, String status, String evidence) {}
}
