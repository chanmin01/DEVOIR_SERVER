package com.benly.feedback.service;

import com.benly.feedback.entity.SessionFormat;

import java.util.List;

public final class TotalScoreCalculator {

    public static final int PRESENTATION_WEIGHT = 30;
    public static final int QA_WEIGHT = 70;

    private TotalScoreCalculator() {
    }

    public record Breakdown(int presentation, int qaAverage, int presentationWeight, int qaWeight) {
    }

    public record Result(int total, Breakdown breakdown) {
    }

    public static Result calculate(SessionFormat format, Integer presentationScore, List<Integer> qaScores) {
        int qaAverage = average(qaScores);
        if (format != SessionFormat.PT || presentationScore == null) {
            return new Result(qaAverage, null);
        }
        int total = Math.round((presentationScore * PRESENTATION_WEIGHT + qaAverage * QA_WEIGHT) / 100f);
        return new Result(total,
                new Breakdown(presentationScore, qaAverage, PRESENTATION_WEIGHT, QA_WEIGHT));
    }

    private static int average(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }
        int sum = scores.stream().mapToInt(Integer::intValue).sum();
        return Math.round((float) sum / scores.size());
    }
}
