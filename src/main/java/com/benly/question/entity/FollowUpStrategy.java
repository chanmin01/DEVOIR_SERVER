package com.benly.question.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 꼬리질문의 접근 전략.
 * 같은 메인 질문에 대해 매번 비슷한 꼬리가 나오는 것을 막고,
 * 채점 시 "그 전략이 의도한 바를 지원자가 충족했는가"를 평가 기준으로 쓰기 위해 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum FollowUpStrategy {

    // 기술 면접
    WHY("근거", "그 선택을 한 이유와, 검토했지만 택하지 않은 대안을 캐묻는다."),
    DEEP("심화", "답변에서 언급된 개념이나 기술을 한 단계 더 깊이 파고든다."),
    WHAT_IF("가정", "규모·제약·환경이 달라졌을 때 어떻게 대응할지 묻는다."),

    // 인성 면접
    ACTION("행동", "본인이 실제로 한 행동을 단계별로 구체화하게 한다."),
    RESULT("결과", "그 행동이 만든 결과를 수치나 관찰 가능한 변화로 말하게 한다."),
    REFLECTION("회고", "그 경험에서 배운 점과 다음에 바꿀 점을 묻는다.");

    private final String label;
    private final String guide;
}