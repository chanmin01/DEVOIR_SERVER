package com.benly.feedback.dto;

import com.benly.feedback.entity.FeedbackStatus;

/**
 * 최종 채점에서 카드 상태를 읽어 판단할 때 쓰는 값. 트랜잭션 밖에서 엔티티를 쓰지 않으려고 복사해 둔다.
 * inProgress는 다른 곳에서 채점 중이고 아직 멈춘 것으로 보기 전인 상태다.
 */
public record CardSnapshot(
        Long questionId,          // 발표 카드는 null
        boolean presentation,
        FeedbackStatus status,
        boolean inProgress,
        Integer topicScore,
        String shortTitle,
        FeedbackContent content   // 완료된 카드만 채워짐
) {
    public boolean completed() {
        return status == FeedbackStatus.COMPLETED;
    }
}
