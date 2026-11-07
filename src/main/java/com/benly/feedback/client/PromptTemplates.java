package com.benly.feedback.client;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptTemplates {

    public static final String SCORING_MAIN_SYSTEM = "scoring-main-system";
    public static final String SCORING_MAIN_USER = "scoring-main-user";
    public static final String SCORING_SUMMARY_SYSTEM = "scoring-summary-system";
    public static final String SCORING_SUMMARY_USER = "scoring-summary-user";

    private static final List<String> REQUIRED = List.of(
            SCORING_MAIN_SYSTEM, SCORING_MAIN_USER, SCORING_SUMMARY_SYSTEM, SCORING_SUMMARY_USER);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> templates = new HashMap<>();

    public PromptTemplates() {
        for (String name : REQUIRED) {
            templates.put(name, load(name));
        }
    }

    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("등록되지 않은 프롬프트: " + name);
        }
        return template;
    }

    public String render(String name, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(get(name));
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalStateException("프롬프트 %s에 %s 값이 없습니다".formatted(name, key));
            }
            String value = values.get(key) == null ? "" : values.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String load(String name) {
        ClassPathResource resource = new ClassPathResource("prompts/" + name + ".txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽지 못했습니다: " + name, e);
        }
    }
}
