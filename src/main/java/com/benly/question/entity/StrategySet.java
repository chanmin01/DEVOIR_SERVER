package com.benly.question.entity;

import java.util.ArrayList;
import java.util.List;

import static com.benly.question.entity.FollowUpStrategy.*;

/**
 * 면접 단계별로 쓸 수 있는 전략 묶음.
 * Session.stage 가 String 이므로 AxisSet.resolve 와 같은 방식으로 문자열을 받는다.
 */
public enum StrategySet {

    TECHNICAL(List.of(WHY, DEEP, WHAT_IF)),
    PERSONALITY(List.of(ACTION, RESULT, REFLECTION));

    private final List<FollowUpStrategy> strategies;

    StrategySet(List<FollowUpStrategy> strategies) {
        this.strategies = strategies;
    }

    public List<FollowUpStrategy> strategies() {
        return strategies;
    }

    public static StrategySet resolve(String stage) {
        return "PERSONALITY".equals(stage) ? PERSONALITY : TECHNICAL;
    }

    /**
     * 이미 쓴 전략을 뺀 후보 목록.
     * 전부 소진되면(이론상 꼬리 3개 이상) 전체를 그대로 돌려준다 — 빈 목록을 내보내면
     * Claude 요청의 enum 제약이 깨진다.
     */
    public List<FollowUpStrategy> remaining(List<FollowUpStrategy> used) {
        if (used == null || used.isEmpty()) {
            return strategies;
        }
        List<FollowUpStrategy> rest = new ArrayList<>(strategies);
        rest.removeAll(used);
        return rest.isEmpty() ? strategies : rest;
    }
}