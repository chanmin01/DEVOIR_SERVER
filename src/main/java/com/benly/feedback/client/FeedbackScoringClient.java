package com.benly.feedback.client;

import com.benly.feedback.dto.FeedbackContent;
import com.benly.feedback.dto.ScoringContext;
import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.MainQuestionScore;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.dto.ScoringResult.PresentationScore;
import com.benly.feedback.dto.ScoringResult.SessionSummary;
import com.benly.feedback.dto.ScoringResult.TailInput;
import com.benly.feedback.entity.Axis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 면접 답변 채점용 Claude 호출 클라이언트.
 * 프롬프트 본문은 resources/prompts 아래 파일에 있고, 여기서는 값만 채워 호출한다.
 * 툴은 strict 모드로 호출한다. strict 모드는 스키마의 필드 순서대로 생성하므로
 * 스키마는 항상 LinkedHashMap으로 만들어 순서를 고정한다.
 */
@Slf4j
@Component
public class FeedbackScoringClient {

    private static final int MAIN_MAX_TOKENS = 6000;
    private static final int PRESENTATION_MAX_TOKENS = 4000;
    private static final int SUMMARY_MAX_TOKENS = 1500;
    private static final int MAX_ATTEMPTS = 2;
    private static final int RAW_LOG_LIMIT = 3000;
    private static final Set<String> REQUIREMENT_STATUSES = Set.of("COVERED", "PARTIAL", "MISSING");
    // 문장 안에 다음 필드가 섞여 들어온 흔적. 예: ...했습니다.", "shortTitle": "...
    private static final Pattern LEAKED_JSON = Pattern.compile("\"\\s*,\\s*\"\\w+\"\\s*:");

    private final RestClient restClient;
    private final PromptTemplates prompts;
    // strict 모드가 기본 방어선이고, 이 파서는 혹시 문자열로 감싸 온 경우를 위한 예비 방어선
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();
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
        Map<String, Object> inputSchema = mainSchema(axes, input.tails().size());

        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            JsonNode toolInput = callTool(system, user, "score_answer",
                    "면접 답변을 채점하여 축별 점수와 피드백을 반환합니다.", inputSchema, MAIN_MAX_TOKENS);
            try {
                return parseMainScore(axes, input.tails().size(), input.answer(), toolInput);
            } catch (IllegalStateException e) {
                lastError = e;
                log.warn("채점 응답 형식 오류 attempt={}/{} reason={}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw lastError;
    }

    public PresentationScore scorePresentation(List<Axis> axes, ScoringContext context, PresentationInput input) {
        String system = prompts.get(PromptTemplates.SCORING_PRESENTATION_SYSTEM);
        String user = buildPresentationUser(axes, context, input);
        Map<String, Object> inputSchema = presentationSchema(axes, input.requirements().size());

        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            JsonNode toolInput = callTool(system, user, "score_presentation",
                    "PT 발표를 채점하여 축별 점수, 요구사항 판정, 개선 개요를 반환합니다.",
                    inputSchema, PRESENTATION_MAX_TOKENS);
            try {
                return parsePresentation(axes, input.requirements(), toolInput);
            } catch (IllegalStateException e) {
                lastError = e;
                log.warn("발표 채점 응답 형식 오류 attempt={}/{} reason={}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw lastError;
    }

    public SessionSummary summarize(ScoringContext context, int totalScore,
                                    List<MainQuestionScore> perQuestion,
                                    PresentationScore presentation,
                                    int totalSkipped) {
        String system = prompts.get(PromptTemplates.SCORING_SUMMARY_SYSTEM);
        String user = buildSummaryUser(context, totalScore, perQuestion, presentation, totalSkipped);

        Map<String, Object> inputSchema = objectSchema(
                ordered(
                        "summary", stringField("면접 전체 총평, 해요체 두 문장 이내, 점수 숫자 금지"),
                        "keyCoachingWeakness", stringField("반복된 약점 하나, 한 문장"),
                        "keyCoachingAction", stringField(
                                "다음 면접 답변에서 바로 쓸 말하기 방법 하나, 한두 문장, 예시의 숫자는 [수치] 빈칸")
                ),
                List.of("summary", "keyCoachingWeakness", "keyCoachingAction"),
                null
        );

        JsonNode toolInput = callTool(system, user, "summarize_session",
                "면접 전체를 종합하여 총평과 핵심 코칭을 반환합니다.", inputSchema, SUMMARY_MAX_TOKENS);

        return new SessionSummary(
                text(toolInput, "summary"),
                text(toolInput, "keyCoachingWeakness"),
                text(toolInput, "keyCoachingAction")
        );
    }

    // ===== 스키마. 모두 순서가 고정된 LinkedHashMap으로 만든다 =====

    /** 키와 값을 번갈아 받아 순서가 고정된 맵을 만든다 */
    private Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private Map<String, Object> stringField(String description) {
        return ordered("type", "string", "description", description);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required,
                                             String description) {
        Map<String, Object> schema = ordered(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false
        );
        if (description != null) {
            schema.put("description", description);
        }
        return schema;
    }

    private Map<String, Object> arraySchema(Map<String, Object> items, String description) {
        return ordered("type", "array", "items", items, "description", description);
    }

    private Map<String, Object> axisScoreSchema(List<Axis> axes) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Axis axis : axes) {
            props.put(axis.code(), ordered(
                    "type", "integer",
                    "description", axis.label() + " 점수 0~100, 점수 기준 구간에 맞춰 매김"
            ));
        }
        return objectSchema(props, axes.stream().map(Axis::code).collect(Collectors.toList()), null);
    }

    private Map<String, Object> mainSchema(List<Axis> axes, int tailCount) {
        Map<String, Object> tailItemSchema = objectSchema(
                ordered(
                        "tailNumber", ordered("type", "integer", "description",
                                "평가하는 꼬리 질문의 번호, [꼬리 질문 N]의 N"),
                        "good", stringField("이 꼬리 답변에서 잘한 점, 해요체 2문장 이내"),
                        "weak", stringField("이 꼬리 답변에서 빠진 점을 사실만 짚어 해요체 2문장 이내"),
                        "next", stringField("이 꼬리 답변 다음에 해볼 말하기 방법 하나, 예시의 숫자는 [수치] 빈칸"),
                        "weakAxis", stringField("이 꼬리 답변의 가장 약한 축과 이유 한 문장"),
                        "improvedAfter", stringField("이 꼬리 답변 내용만으로 결론 먼저 고쳐 쓴 답변 4~5문장, 새 사실 금지")
                ),
                List.of("tailNumber", "good", "weak", "next", "weakAxis", "improvedAfter"),
                null
        );

        return objectSchema(
                ordered(
                        "shortTitle", stringField("메인 질문만 요약한 15자 안팎 명사구"),
                        "axisScores", axisScoreSchema(axes),
                        "good", stringField("메인 답변에서 잘한 점, 해요체 2문장 이내"),
                        "weak", stringField("메인 답변에서 빠진 점, 해요체 2문장 이내"),
                        "next", stringField("메인 답변 다음에 해볼 말하기 방법 하나, 예시의 숫자는 [수치] 빈칸"),
                        "weakAxis", stringField("메인 답변의 가장 약한 축과 이유 한 문장"),
                        "improvedAfter", stringField(
                                "메인 답변 내용만으로 결론 먼저 고쳐 쓴 답변 4~5문장, 꼬리 답변 내용과 새 사실 금지"),
                        "tails", arraySchema(tailItemSchema,
                                "꼬리 질문별 피드백, 꼬리 질문 1번부터 %d번까지 순서대로 하나씩".formatted(tailCount))
                ),
                List.of("shortTitle", "axisScores", "good", "weak", "next", "weakAxis", "improvedAfter", "tails"),
                null
        );
    }

    private Map<String, Object> presentationSchema(List<Axis> axes, int requirementCount) {
        Map<String, Object> requirementItem = objectSchema(
                ordered(
                        "status", ordered(
                                "type", "string",
                                "enum", List.of("COVERED", "PARTIAL", "MISSING"),
                                "description", "요구사항을 다뤘으면 COVERED, 일부만이면 PARTIAL, 안 다뤘으면 MISSING"),
                        "evidence", stringField(
                                "COVERED면 근거 발표 문장 그대로, PARTIAL이면 부족한 점 해요체 한 문장, MISSING이면 빈 문자열")
                ),
                List.of("status", "evidence"),
                null
        );

        Map<String, Object> stringList = ordered("type", "array", "items", ordered("type", "string"));

        return objectSchema(
                ordered(
                        "axisScores", axisScoreSchema(axes),
                        "good", stringField("발표에서 잘한 점, 해요체 2문장 이내"),
                        "weak", stringField("발표에서 빠진 점, 해요체 2문장 이내"),
                        "next", stringField("다음 발표에서 해볼 방법 하나, 예시의 숫자는 [수치] 빈칸"),
                        "weakAxis", stringField("발표의 가장 약한 축과 이유 한 문장"),
                        "requirements", arraySchema(requirementItem,
                                "발표 요구사항별 판정, 정확히 %d개를 주어진 순서대로".formatted(requirementCount)),
                        "improvedOutline", objectSchema(
                                ordered("before", stringList, "after", stringList),
                                List.of("before", "after"),
                                "before는 실제 발표 흐름 요약, after는 더 나은 순서로 다시 짠 개요")
                ),
                List.of("axisScores", "good", "weak", "next", "weakAxis", "requirements", "improvedOutline"),
                null
        );
    }

    // ===== 호출 =====
    private JsonNode callTool(String system, String user, String toolName, String toolDesc,
                              Map<String, Object> inputSchema, int maxTokens) {
        Map<String, Object> requestBody = ordered(
                "model", model,
                "max_tokens", maxTokens,
                "temperature", 0.2,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)),
                "tools", List.of(ordered(
                        "name", toolName,
                        "description", toolDesc,
                        "strict", true,
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

        String stopReason = response.path("stop_reason").asText("");
        log.info("채점 호출 tool={} {}ms inputTokens={} outputTokens={} stopReason={}",
                toolName,
                System.currentTimeMillis() - startedAt,
                response.path("usage").path("input_tokens").asInt(-1),
                response.path("usage").path("output_tokens").asInt(-1),
                stopReason);

        if ("max_tokens".equals(stopReason)) {
            log.warn("채점 응답이 max_tokens에서 잘림 tool={} maxTokens={}", toolName, maxTokens);
        }

        for (JsonNode content : response.path("content")) {
            if ("tool_use".equals(content.path("type").asText())) {
                return content.path("input");
            }
        }
        throw new IllegalStateException("채점 응답 파싱 실패: tool_use 블록 없음 stopReason=" + stopReason);
    }

    // ===== 파싱 =====

    private Map<String, Integer> parseAxisScores(List<Axis> axes, JsonNode in) {
        Map<String, Integer> axisScores = new HashMap<>();
        JsonNode scoresNode = asJson(in.path("axisScores"));
        for (Axis axis : axes) {
            axisScores.put(axis.code(), clamp(parseInt(scoresNode, axis.code())));
        }
        return axisScores;
    }

    private MainQuestionScore parseMainScore(List<Axis> axes, int expectedTailCount,
                                             String mainAnswer, JsonNode in) {
        Map<String, Integer> axisScores = parseAxisScores(axes, in);
        List<FeedbackContent.TailContent> tails = parseTails(expectedTailCount, in);

        FeedbackContent content = new FeedbackContent(
                cleanText(in, "good"),
                cleanText(in, "weak"),
                cleanText(in, "next"),
                cleanText(in, "weakAxis"),
                new FeedbackContent.ImprovedAnswer(mainAnswer, cleanText(in, "improvedAfter")),
                tails
        );

        return new MainQuestionScore(cleanText(in, "shortTitle"), axisScores, content);
    }

    /**
     * 꼬리 피드백을 tailNumber 기준으로 제자리에 놓는다.
     * 모델이 배열 순서를 바꿔 보내도 질문과 피드백이 어긋나지 않게 한다.
     */
    private List<FeedbackContent.TailContent> parseTails(int expectedTailCount, JsonNode in) {
        List<JsonNode> nodes = new ArrayList<>();
        asJson(in.path("tails")).forEach(nodes::add);

        if (nodes.size() != expectedTailCount) {
            log.warn("꼬리 개수 불일치 원본 응답: {}", abbreviate(in.toString()));
            throw new IllegalStateException(
                    "꼬리 피드백 개수 불일치: 기대=%d, 실제=%d".formatted(expectedTailCount, nodes.size()));
        }

        FeedbackContent.TailContent[] slots = new FeedbackContent.TailContent[expectedTailCount];
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode t = nodes.get(i);
            int number = parseInt(t, "tailNumber");
            int slot = (number >= 1 && number <= expectedTailCount) ? number - 1 : i;

            if (slots[slot] != null) {
                log.warn("꼬리 번호 중복 원본 응답: {}", abbreviate(in.toString()));
                throw new IllegalStateException("꼬리 피드백 번호 중복: " + number);
            }
            if (slot != i) {
                log.info("꼬리 피드백 순서 보정 받은위치={} 번호={}", i + 1, number);
            }

            slots[slot] = new FeedbackContent.TailContent(
                    cleanText(t, "good"),
                    cleanText(t, "weak"),
                    cleanText(t, "next"),
                    cleanText(t, "weakAxis"),
                    cleanText(t, "improvedAfter")
            );
        }
        return Arrays.asList(slots);
    }

    private PresentationScore parsePresentation(List<Axis> axes, List<String> requirementTexts, JsonNode in) {
        Map<String, Integer> axisScores = parseAxisScores(axes, in);

        List<JsonNode> reqNodes = new ArrayList<>();
        asJson(in.path("requirements")).forEach(reqNodes::add);
        if (reqNodes.size() != requirementTexts.size()) {
            log.warn("요구사항 개수 불일치 원본 응답: {}", abbreviate(in.toString()));
            throw new IllegalStateException(
                    "요구사항 판정 개수 불일치: 기대=%d, 실제=%d".formatted(requirementTexts.size(), reqNodes.size()));
        }

        List<FeedbackContent.RequirementContent> requirements = IntStream.range(0, reqNodes.size())
                .mapToObj(i -> {
                    String status = nvl(text(reqNodes.get(i), "status")).trim().toUpperCase();
                    if (!REQUIREMENT_STATUSES.contains(status)) {
                        status = "MISSING";
                    }
                    String evidence = cleanText(reqNodes.get(i), "evidence");
                    if ("MISSING".equals(status) || evidence == null || evidence.isBlank()) {
                        evidence = null;
                    }
                    return new FeedbackContent.RequirementContent(requirementTexts.get(i), status, evidence);
                })
                .toList();

        JsonNode outlineNode = asJson(in.path("improvedOutline"));
        FeedbackContent.OutlineContent outline = new FeedbackContent.OutlineContent(
                stringList(outlineNode.path("before")),
                stringList(outlineNode.path("after"))
        );

        FeedbackContent content = FeedbackContent.presentation(
                cleanText(in, "good"),
                cleanText(in, "weak"),
                cleanText(in, "next"),
                cleanText(in, "weakAxis"),
                requirements,
                outline
        );
        return new PresentationScore(axisScores, content);
    }

    /** 문장 안에 JSON 조각이 섞여 있으면 예외를 던져 재시도하게 한다 */
    private String cleanText(JsonNode node, String field) {
        String v = text(node, field);
        if (v != null && LEAKED_JSON.matcher(v).find()) {
            log.warn("문장에 JSON 조각 섞임 field={} 값={}", field, abbreviate(v));
            throw new IllegalStateException("문장에 JSON 조각 섞임: " + field);
        }
        return v;
    }

    private List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : asJson(node)) {
            String v = n.asText();
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
        return out;
    }

    /**
     * 배열이나 객체가 JSON 문자열로 감싸져 오는 경우를 풀어서 돌려준다.
     */
    private JsonNode asJson(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return node;
        }
        String raw = node.asText().trim();
        if (!(raw.startsWith("[") || raw.startsWith("{"))) {
            return node;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("문자열 JSON 파싱 실패: {} 원본: {}", e.getMessage(), abbreviate(raw));
            return node;
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > RAW_LOG_LIMIT ? s.substring(0, RAW_LOG_LIMIT) : s;
    }

    // ===== 프롬프트 조립 =====

    private String axisGuide(List<Axis> axes) {
        return axes.stream()
                .map(a -> "- %s %s: 가중치 %d%%".formatted(a.code(), a.label(), a.weight()))
                .collect(Collectors.joining("\n"));
    }

    private String buildMainUser(List<Axis> axes, ScoringContext ctx, MainQuestionInput input) {
        StringBuilder tailText = new StringBuilder();
        if (input.tails() != null) {
            int idx = 1;
            for (TailInput t : input.tails()) {
                tailText.append("""
                        [꼬리 질문 %d] 전략: %s
                        <tail_question>%s</tail_question>
                        <tail_answer>%s</tail_answer>
                        """.formatted(idx++, nvl(t.strategy()), nvl(t.question()), nvl(t.answer())));
            }
        }

        return prompts.render(PromptTemplates.SCORING_MAIN_USER, Map.of(
                "companyType", nvl(ctx.companyType()),
                "stage", nvl(ctx.stage()),
                "jobTitle", nvl(ctx.jobTitle()),
                "axes", axisGuide(axes),
                "question", nvl(input.question()),
                "answer", nvl(input.answer()),
                "tails", tailText.isEmpty() ? "꼬리 질문 없음" : tailText.toString()
        ));
    }

    private String buildPresentationUser(List<Axis> axes, ScoringContext ctx, PresentationInput in) {
        String constraints = in.constraints().stream()
                .map(c -> "- " + c)
                .collect(Collectors.joining("\n"));

        String requirements = IntStream.range(0, in.requirements().size())
                .mapToObj(i -> "%d. %s".formatted(i + 1, in.requirements().get(i)))
                .collect(Collectors.joining("\n"));

        String duration = in.durationSec() == null
                ? "기록 없음"
                : "%d분 %d초".formatted(in.durationSec() / 60, in.durationSec() % 60);

        return prompts.render(PromptTemplates.SCORING_PRESENTATION_USER, Map.ofEntries(
                Map.entry("companyType", nvl(ctx.companyType())),
                Map.entry("jobTitle", nvl(ctx.jobTitle())),
                Map.entry("taskTitle", nvl(in.taskTitle())),
                Map.entry("problemSituation", nvl(in.problemSituation())),
                Map.entry("constraints", constraints.isEmpty() ? "없음" : constraints),
                Map.entry("trackLabel", nvl(in.trackLabel())),
                Map.entry("trackGuidance", nvl(in.trackGuidance())),
                Map.entry("requirements", requirements),
                Map.entry("axes", axisGuide(axes)),
                Map.entry("duration", duration),
                Map.entry("transcript", nvl(in.transcript()))
        ));
    }

    private String buildSummaryUser(ScoringContext ctx, int totalScore,
                                    List<MainQuestionScore> perQuestion,
                                    PresentationScore presentation,
                                    int totalSkipped) {
        StringBuilder perQ = new StringBuilder();

        if (presentation != null) {
            FeedbackContent pc = presentation.content();
            perQ.append("[발표]\n");
            if (pc.good() == null && pc.weak() == null) {
                perQ.append("- 발표 내용이 기록되지 않았습니다\n\n");
            } else {
                perQ.append("- 잘한 점: ").append(nvl(pc.good())).append("\n");
                perQ.append("- 약점: ").append(nvl(pc.weak())).append("\n\n");
            }
        }

        for (MainQuestionScore q : perQuestion) {
            FeedbackContent c = q.content();
            perQ.append("[").append(nvl(q.shortTitle())).append("]\n");
            perQ.append("- 잘한 점: ").append(nvl(c.good())).append("\n");
            perQ.append("- 약점: ").append(nvl(c.weak())).append("\n");

            List<FeedbackContent.TailContent> tails = c.tails() == null ? List.of() : c.tails();
            for (int i = 0; i < tails.size(); i++) {
                perQ.append("- 꼬리 ").append(i + 1).append(" 약점: ")
                        .append(nvl(tails.get(i).weak())).append("\n");
            }
            perQ.append("\n");
        }

        String skipNote = totalSkipped > 0
                ? "지원자는 질문 %d개를 건너뛰었습니다. 총평에 이 점을 한 번만 짚어 주세요.".formatted(totalSkipped)
                : "건너뛴 질문은 없습니다.";

        return prompts.render(PromptTemplates.SCORING_SUMMARY_USER, Map.of(
                "companyType", nvl(ctx.companyType()),
                "stage", nvl(ctx.stage()),
                "totalScore", String.valueOf(totalScore),
                "skipNote", skipNote,
                "perQuestion", perQ.toString().trim()
        ));
    }

    // ===== 공통 =====

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
