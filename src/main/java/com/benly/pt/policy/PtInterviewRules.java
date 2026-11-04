package com.benly.pt.policy;

import java.time.Duration;
import java.util.List;

public final class PtInterviewRules {

    public static final List<Integer> PREPARATION_MINUTES =
            List.of(15, 20);

    public static final int PRESENTATION_SECONDS = 300;

    public static final int MAIN_QUESTION_COUNT = 2;

    public static final int FOLLOW_UP_QUESTION_COUNT_PER_MAIN = 2;

    public static final int TOTAL_QUESTION_COUNT =
            MAIN_QUESTION_COUNT
                    * (1 + FOLLOW_UP_QUESTION_COUNT_PER_MAIN);

    public static final Duration GUEST_RETENTION =
            Duration.ofHours(24);

    private PtInterviewRules() {
    }
}