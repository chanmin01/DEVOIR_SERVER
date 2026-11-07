package com.benly.feedback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeedbackContent(
        String good,
        String weak,
        String next,
        String weakAxis,
        ImprovedAnswer improvedAnswer,
        List<TailContent> tails,
        List<RequirementContent> requirements,   // 발표 카드만
        OutlineContent improvedOutline           // 발표 카드만
) {
    /** 질문 카드용. 기존 코드가 쓰던 생성자 */
    public FeedbackContent(String good, String weak, String next, String weakAxis,
                           ImprovedAnswer improvedAnswer, List<TailContent> tails) {
        this(good, weak, next, weakAxis, improvedAnswer, tails, null, null);
    }

    /** 발표 카드용 */
    public static FeedbackContent presentation(String good, String weak, String next, String weakAxis,
                                               List<RequirementContent> requirements,
                                               OutlineContent improvedOutline) {
        return new FeedbackContent(good, weak, next, weakAxis, null, List.of(), requirements, improvedOutline);
    }

    public record ImprovedAnswer(
            String before,
            String after
    ) {
    }

    /**
     * strategy 를 제거했다. 모델이 지어내던 값이고 읽는 쪽이 없었다.
     * 전략은 questions.strategy(엔티티)가 유일한 출처다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TailContent(
            String good,
            String weak,
            String next,
            String weakAxis,
            String improvedAfter
    ) {
    }

    /** status: COVERED, PARTIAL, MISSING */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequirementContent(
            String requirement,
            String status,
            String evidence
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutlineContent(
            List<String> before,
            List<String> after
    ) {
    }
}
