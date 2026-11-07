package com.benly.feedback.dto;

import java.util.List;

/**
 * 리포트 조회(GET /sessions/{id}/feedback) 응답. 노션 명세 기준.
 * 채점 중(status SCORING)이나 채점 실패(status FAILED)에도 조회할 수 있고, 그때는 끝난 카드만 피드백이 채워진다.
 * 총점, 총평, 핵심 코칭, 레이더, 점수 구성은 세션 채점이 끝난 뒤에만 채워진다.
 */
public record FeedbackReportResponse(
        Long sessionId,
        String status,                    // SCORING / COMPLETED / FAILED
        Meta meta,
        KeyCoaching keyCoaching,          // 채점이 끝나지 않았으면 null
        Integer totalScore,               // 채점이 끝나지 않았으면 null
        ScoreBreakdown scoreBreakdown,    // PT이고 채점이 끝났을 때만
        String verdict,                   // 채점이 끝나지 않았으면 null
        Radar radar,                      // 채점이 끝나지 않았으면 null
        List<Card> cards
) {

    public record Meta(
            String format,
            String companyType,
            String stage,
            String companyName,
            String jobTitle,
            String createdAt
    ) {
    }

    public record KeyCoaching(
            String weakness,
            String action
    ) {
    }

    // PT만. QA는 null
    public record ScoreBreakdown(
            int presentation,
            int qaAverage,
            int presentationWeight,
            int qaWeight
    ) {
    }

    public record Radar(
            String type,
            List<Axis> axes
    ) {
        public record Axis(
                String axis,
                String label,
                Integer score
        ) {
        }
    }

    public record Card(
            String cardType,
            String status,                    // SCORING / COMPLETED / FAILED. COMPLETED가 아니면 피드백 필드는 null
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
    ) {
    }

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
    ) {
    }

    public record ImprovedAnswer(String before, String after) {
    }

    public record ImprovedOutline(List<String> before, List<String> after) {
    }

    public record Requirement(String requirement, String status, String evidence) {
    }
}
