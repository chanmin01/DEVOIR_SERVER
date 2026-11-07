package com.benly.feedback.entity;

import com.benly.session.entity.CompanyType;

import java.util.List;
import java.util.Map;

public enum AxisSet {

    TECHNICAL(List.of(
            new Axis("ACCURACY", "정확성", 25),
            new Axis("DEPTH", "깊이", 20),
            new Axis("PROBLEM_SOLVING", "문제 해결", 20),
            new Axis("TECH_RATIONALE", "기술 선택 근거", 15),
            new Axis("EXPLANATION", "설명력", 10),
            new Axis("RESULT_IMPACT", "결과·임팩트", 10)
    )),
    FINANCE_TECHNICAL(List.of(
            new Axis("ACCURACY", "정확성", 20),
            new Axis("DEPTH", "깊이", 20),
            new Axis("PROBLEM_SOLVING", "문제 해결", 20),
            new Axis("TECH_RATIONALE", "기술 선택 근거", 15),
            new Axis("EXPLANATION", "설명력", 10),
            new Axis("FINANCE_DOMAIN", "금융 도메인 이해", 15)
    )),
    PERSONALITY(List.of(
            new Axis("SITUATION_TASK", "상황·과제", 15),
            new Axis("ACTION", "행동 구체성", 25),
            new Axis("CONTRIBUTION", "본인 기여", 20),
            new Axis("RESULT", "결과 제시", 20),
            new Axis("AUTHENTICITY", "진정성·일관성", 10),
            new Axis("FIT", "직무·조직 적합", 10)
    )),
    PT(List.of(
            new Axis("STRUCTURE", "구조·논리 전개", 20),
            new Axis("RELEVANCE", "과제 적합성", 20),
            new Axis("EVIDENCE", "근거·구체성", 20),
            new Axis("DEPTH", "이해 깊이·실행 가능성", 15),
            new Axis("DELIVERY", "전달력·설득력", 15),
            new Axis("CONSISTENCY", "일관성", 10)
    ));

    private final List<Axis> axes;

    AxisSet(List<Axis> axes) {
        this.axes = axes;
    }

    public List<Axis> axes() {
        return axes;
    }

    /**
     * 질문 카드에 쓸 축 세트
     * - 인성 면접이면 PERSONALITY
     * - 기술 면접인데 금융IT면 FINANCE_TECHNICAL, 그 외 TECHNICAL
     */
    public static AxisSet resolve(String stage, String companyType) {
        if ("PERSONALITY".equals(stage)) {
            return PERSONALITY;
        }
        if (CompanyType.FINANCE_IT.name().equals(companyType)) {
            return FINANCE_TECHNICAL;
        }
        return TECHNICAL;
    }

    /**
     * 육각형에 쓸 축 세트. PT는 발표 카드 축만 그린다.
     */
    public static AxisSet forRadar(SessionFormat format, String stage, String companyType) {
        return format == SessionFormat.PT ? PT : resolve(stage, companyType);
    }

    public int weightedScore(Map<String, Integer> rawScores) {
        int sum = 0;
        for (Axis axis : axes) {
            Integer raw = rawScores.getOrDefault(axis.code(), 0);
            sum += raw * axis.weight();
        }
        return Math.round(sum / 100f);
    }
}
