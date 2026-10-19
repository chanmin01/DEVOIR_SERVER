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
        List<TailContent> tails
) {
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
}