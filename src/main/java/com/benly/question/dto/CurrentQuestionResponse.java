package com.benly.question.dto;

import com.benly.question.entity.Question;

public record CurrentQuestionResponse(
        Long questionId,
        Integer seq,
        String content,
        String type,       // MAIN / FOLLOW_UP
        String strategy    // 꼬리질문일 때만, 메인은 null
) {
    public static CurrentQuestionResponse from(Question question) {
        return new CurrentQuestionResponse(
                question.getId(),
                question.getSeq(),
                question.getContent(),
                question.getType(),
                question.getStrategy() == null ? null : question.getStrategy().name()
        );
    }
}