package com.benly.feedback.client;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.entity.Axis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 면접 답변 채점용 Claude 호출 클라이언트.
 * 프롬프트 본문은 resources/prompts 아래 파일에 있고, 여기서는 값만 채워 호출한다.
 */
@Slf4j
@Component
public class FeedbackScoringClient {

    private final RestClient restClient;
    private final PromptTemplates prompts;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public FeedbackScoringClient(
            PromptTemplates prompts,
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.api-url}") String apiUrl,
            @Value("${claude.model}") String model
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(180000);

        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.prompts = prompts;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
    }

    public MainQuestionScore scoreMainQuestion(List<Axis> axes, ScoringContext context, MainQuestionInput input) {
        String system = prompts.get(PromptTemplates.SCORING_MAIN_SYSTEM);
        String user = buildMainUser(axes, context, input);

        Map<String, Object> axisScoreSchema = new HashMap<>();
        for (Axis axis : axes) {
            axisScoreSchema.put(axis.code(), Map.of(
                    "type", "integer",
                    "description", axis.label() + " 점수 0~100, 점수 기준 구간에 맞춰 매김"
            ));
        }

        Map<String, Object> tailItemSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "good", Map.of("type", "string", "description", "잘한 점, 해요체 2문장 이내"),
                        "weak", Map.of("type", "string", "description", "빠진 점을 사실만 짚어 해요체 2문장 이내"),
                        "next", Map.of("type", "string", "description", "다음 답변에서 해볼 말하기 방법 하나"),
                        "weakAxis", Map.of("type", "string", "description", "가장 약한 축과 이유 한 문장"),
                        "improvedAfter", Map.of("type", "string", "description",
                                "고친 답변 4~5문장, 말하지 않은 수치와 결과는 [수치] [결과] 빈칸")
                ),
                "required", List.of("good", "weak", "next", "weakAxis", "improvedAfter")
        );

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "shortTitle", Map.of("type", "string", "description", "메인 질문만 요약한 15자 안팎 명사구"),
                        "axisScores", Map.of(
                                "type", "object",
                                "properties", axisScoreSchema,
                                "required", axes.stream().map(Axis::code).collect(Collectors.toList())
                        ),
                        "good", Map.of("type", "string", "description", "메인 답변에서 잘한 점, 해요체 2문장 이내"),
                        "weak", Map.of("type", "string", "description", "메인 답변에서 빠진 점, 해요체 2문장 이내"),
                        "next", Map.of("type", "string", "description", "다음 답변에서 해볼 말하기 방법 하나"),
                        "weakAxis", Map.of("type", "string", "description", "가장 약한 축과 이유 한 문장"),
                        "improvedAfter", Map.of("type", "string", "description",
                                "메인 답변을 고친 답변 4~5문장, 말하지 않은 수치와 결과는 [수치] [결과] 빈칸"),
                        "tails", Map.of(
                                "type", "array",
                                "items", tailItemSchema,
                                "minItems", input.tails().size(),
                                "maxItems", input.tails().size(),
                                "description", "꼬리 질문별 피드백, 꼬리 질문 수와 같은 개수로 순서 유지"
                        )
                ),
                "required", List.of("shortTitle", "axisScores", "good", "weak", "next", "weakAxis",
                        "improvedAfter", "tails")
        );

        JsonNode toolInput = callTool(system, user, "score_answer",
                "면접 답변을 채점하여 축별 점수와 피드백을 반환합니다.", inputSchema, 4000);

        return parseMainScore(axes, input.tails().size(), input.answer(), toolInput);
    }

    public SessionSummary summarize(ScoringContext context, int totalScore,
                                    List<MainQuestionScore> perQuestion, int totalSkipped) {
        String system = prompts.get(PromptTemplates.SCORING_SUMMARY_SYSTEM);
        String user = buildSummaryUser(context, totalScore, perQuestion, totalSkipped);

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "summary", Map.of("type", "string", "description", "면접 전체 총평, 해요체 두 문장 이내"),
                        "keyCoachingWeakness", Map.of("type", "string", "description", "반복된 약점 하나, 한 문장"),
                        "keyCoachingAction", Map.of("type", "string", "description",
                                "다음 면접 답변에서 바로 쓸 말하기 방법 하나, 한두 문장")
                ),
                "required", List.of("summary", "keyCoachingWeakness", "keyCoachingAction")
        );

        JsonNode toolInput = callTool(system, user, "summarize_session",
                "면접 전체를 종합하여 총평과 핵심 코칭을 반환합니다.", inputSchema, 1500);

        return new SessionSummary(
                text(toolInput, "summary"),
                text(toolInput, "keyCoachingWeakness"),
                text(toolInput, "keyCoachingAction")
        );
    }

    private JsonNode callTool(String system, String user, String toolName, String toolDesc,
                              Map<String, Object> inputSchema, int maxTokens) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)),
                "tools", List.of(Map.of(
                        "name", toolName,
                        "description", toolDesc,
                        "input_schema", inputSchema
                )),
                "tool_choice", Map.of("type", "tool", "name", toolName)
        );

        long startedAt = System.currentTimeMillis();

        JsonNode response = restClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

        log.info("채점 호출 tool={} {}ms inputTokens={} outputTokens={}",
                toolName,
                System.currentTimeMillis() - startedAt,
                response.path("usage").path("input_tokens").asInt(-1),
                response.path("usage").path("output_tokens").asInt(-1));

        for (JsonNode content : response.path("content")) {
            if ("tool_use".equals(content.path("type").asText())) {
                return content.path("input");
            }
        }
        throw new IllegalStateException("채점 응답 파싱 실패: tool_use 블록 없음");
    }

    private MainQuestionScore parseMainScore(List<Axis> axes, int expectedTailCount,
                                             String mainAnswer, JsonNode in) {
        Map<String, Integer> axisScores = new HashMap<>();
        JsonNode scoresNode = in.path("axisScores");
        for (Axis axis : axes) {
            axisScores.put(axis.code(), clamp(parseInt(scoresNode, axis.code())));
        }

        List<FeedbackContent.TailContent> tails = new ArrayList<>();
        for (JsonNode t : in.path("tails")) {
            tails.add(new FeedbackContent.TailContent(
                    text(t, "good"),
                    text(t, "weak"),
                    text(t, "next"),
                    text(t, "weakAxis"),
                    text(t, "improvedAfter")
            ));
        }

        if (tails.size() != expectedTailCount) {
            throw new IllegalStateException(
                    "꼬리 피드백 개수 불일치: 기대=%d, 실제=%d".formatted(expectedTailCount, tails.size()));
        }

        FeedbackContent content = new FeedbackContent(
                text(in, "good"),
                text(in, "weak"),
                text(in, "next"),
                text(in, "weakAxis"),
                new FeedbackContent.ImprovedAnswer(mainAnswer, text(in, "improvedAfter")),
                tails
        );

        return new MainQuestionScore(text(in, "shortTitle"), axisScores, content);
    }

    private String buildMainUser(List<Axis> axes, ScoringContext ctx, MainQuestionInput input) {
        String axisGuide = axes.stream()
                .map(a -> "- %s %s: 가중치 %d%%".formatted(a.code(), a.label(), a.weight()))
                .collect(Collectors.joining("\n"));

        StringBuilder tailText = new StringBuilder();
        if (input.tails() != null) {
            int idx = 1;
            for (TailInput t : input.tails()) {
                tailText.append("""
                        [꼬리 질문 %d] 전략: %s
                        <question>%s</question>
                        <answer>%s</answer>
                        """.formatted(idx++, nvl(t.strategy()), nvl(t.question()), nvl(t.answer())));
            }
        }

        return prompts.render(PromptTemplates.SCORING_MAIN_USER, Map.of(
                "companyType", nvl(ctx.companyType()),
                "stage", nvl(ctx.stage()),
                "jobTitle", nvl(ctx.jobTitle()),
                "axes", axisGuide,
                "question", nvl(input.question()),
                "answer", nvl(input.answer()),
                "tails", tailText.isEmpty() ? "꼬리 질문 없음" : tailText.toString()
        ));
    }

    private String buildSummaryUser(ScoringContext ctx, int totalScore,
                                    List<MainQuestionScore> perQuestion, int totalSkipped) {
        String perQ = perQuestion.stream()
                .map(q -> "- %s: %s".formatted(nvl(q.shortTitle()), nvl(q.content().weak())))
                .collect(Collectors.joining("\n"));

        String skipNote = totalSkipped > 0
                ? "지원자는 질문 %d개를 건너뛰었습니다. 총평에 이 점을 한 번만 짚어 주세요.".formatted(totalSkipped)
                : "건너뛴 질문은 없습니다.";

        return prompts.render(PromptTemplates.SCORING_SUMMARY_USER, Map.of(
                "companyType", nvl(ctx.companyType()),
                "stage", nvl(ctx.stage()),
                "totalScore", String.valueOf(totalScore),
                "skipNote", skipNote,
                "perQuestion", perQ
        ));
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private int parseInt(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(v.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
