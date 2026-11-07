package com.benly.feedback.dto;

import com.benly.feedback.entity.FeedbackStatus;

public record FeedbackStatusResponse(
        Long sessionId,
        FeedbackStatus status,   // SCORING / COMPLETED / FAILED
        int completedCards,      // 채점이 끝난 카드 수
        int totalCards           // 전체 카드 수. QA는 메인 질문 수, PT는 메인 질문 수 + 발표 1
) {
    public static FeedbackStatusResponse of(Long sessionId, FeedbackStatus status,
                                            int completedCards, int totalCards) {
        return new FeedbackStatusResponse(sessionId, status, completedCards, totalCards);
    }

    public static FeedbackStatusResponse of(Long sessionId, FeedbackStatus status) {
        return new FeedbackStatusResponse(sessionId, status, 0, 0);
    }
}
