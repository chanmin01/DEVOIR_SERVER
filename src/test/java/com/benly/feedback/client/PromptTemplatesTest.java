package com.benly.feedback.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplatesTest {

    private final PromptTemplates prompts = new PromptTemplates();

    @Test
    @DisplayName("채점 프롬프트 파일 4개를 모두 읽는다")
    void loadsAll() {
        assertThat(prompts.get(PromptTemplates.SCORING_MAIN_SYSTEM)).contains("[점수 기준]");
        assertThat(prompts.get(PromptTemplates.SCORING_MAIN_USER)).contains("{{answer}}");
        assertThat(prompts.get(PromptTemplates.SCORING_SUMMARY_SYSTEM)).contains("keyCoachingAction");
        assertThat(prompts.get(PromptTemplates.SCORING_SUMMARY_USER)).contains("{{perQuestion}}");
    }

    @Test
    @DisplayName("답변 안의 {{ }}는 다시 치환하지 않는다")
    void doesNotReplaceInsideValues() {
        String rendered = prompts.render(PromptTemplates.SCORING_SUMMARY_USER, Map.of(
                "companyType", "SERVICE",
                "stage", "TECHNICAL",
                "totalScore", "80",
                "skipNote", "건너뛴 질문은 없습니다.",
                "perQuestion", "- 캐시: {{stage}} 그대로 남아야 함"
        ));

        assertThat(rendered).contains("기업 유형: SERVICE");
        assertThat(rendered).contains("{{stage}} 그대로 남아야 함");
    }

    @Test
    @DisplayName("템플릿 자리에 값이 없으면 예외")
    void missingValue() {
        assertThatThrownBy(() -> prompts.render(PromptTemplates.SCORING_SUMMARY_USER, Map.of("stage", "TECHNICAL")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("companyType");
    }
}
