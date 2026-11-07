package com.benly.feedback.dto;

import com.benly.feedback.entity.Axis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoringResult {

    public record MainQuestionScore(
            String shortTitle,
            Map<String, Integer> axisScores,
            FeedbackContent content
    ) {
        public MainQuestionScore scaledBy(double factor) {
            Map<String, Integer> scaled = new HashMap<>();
            axisScores.forEach((code, v) -> scaled.put(code, (int) Math.round(v * factor)));
            return new MainQuestionScore(shortTitle, scaled, content);
        }

        public static MainQuestionScore skipped(List<Axis> axes, String questionText) {
            Map<String, Integer> zeros = new HashMap<>();
            axes.forEach(a -> zeros.put(a.code(), 0));

            FeedbackContent content = new FeedbackContent(
                    null,
                    "답변을 건너뛰어 평가할 수 없습니다.",
                    "다음에는 짧게라도 답변을 시도해 보세요.",
                    null, null, List.of());

            String title = (questionText == null) ? "건너뛴 질문"
                    : questionText.substring(0, Math.min(20, questionText.length()));
            return new MainQuestionScore(title, zeros, content);
        }
    }

    public record SessionSummary(
            String summary,
            String keyCoachingWeakness,
            String keyCoachingAction
    ) {
    }

    public record MainQuestionInput(
            Integer seq,
            String question,
            String answer,
            List<TailInput> tails
    ) {
        public int totalCount() {
            return 1 + tails.size();
        }

        public int answeredCount() {
            int n = hasText(answer) ? 1 : 0;
            for (TailInput t : tails) {
                if (hasText(t.answer())) n++;
            }
            return n;
        }

        public int skippedCount() {
            return totalCount() - answeredCount();
        }

        public boolean isFullySkipped() {
            return answeredCount() == 0;
        }

        public double completeness() {
            return (double) answeredCount() / totalCount();
        }

        private static boolean hasText(String s) {
            return s != null && !s.isBlank();
        }
    }

    public record TailInput(
            String strategy,
            String question,
            String answer
    ) {
    }

    /** PT 발표 채점 입력. 과제 내용과 발표 원문 */
    public record PresentationInput(
            String taskTitle,
            String problemSituation,
            List<String> constraints,
            String track,
            String trackLabel,
            String trackGuidance,
            List<String> requirements,
            Integer durationSec,
            String transcript
    ) {
        public boolean isMissing() {
            return transcript == null || transcript.isBlank();
        }
    }

    public record PresentationScore(
            Map<String, Integer> axisScores,
            FeedbackContent content
    ) {
        /** 발표가 기록되지 않은 경우. AI를 부르지 않고 0점, 요구사항은 전부 MISSING */
        public static PresentationScore missing(List<Axis> axes, List<String> requirements) {
            Map<String, Integer> zeros = new HashMap<>();
            axes.forEach(a -> zeros.put(a.code(), 0));

            List<FeedbackContent.RequirementContent> reqs = requirements.stream()
                    .map(r -> new FeedbackContent.RequirementContent(r, "MISSING", null))
                    .toList();

            return new PresentationScore(zeros,
                    FeedbackContent.presentation(null, null, null, null, reqs, null));
        }
    }
}
