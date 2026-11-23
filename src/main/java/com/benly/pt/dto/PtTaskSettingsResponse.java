package com.benly.pt.dto;

import com.benly.pt.entity.PtTask;
import com.benly.pt.entity.PtTaskType;
import com.benly.pt.entity.PtTrack;
import com.benly.pt.policy.PtInterviewRules;

import java.util.List;

public record PtTaskSettingsResponse(
        PtTaskType taskType,
        String targetJobRole,
        boolean trackSelectionRequired,
        List<PtTrack> allowedTracks,
        List<Integer> preparationMinutes,
        int presentationSeconds,
        String practiceNotice
) {

    private static final String NOTICE =
            "직접 제작한 연습용 과제이며 실제 기출 및 공식 전형과 다를 수 있습니다.";

    public static PtTaskSettingsResponse from(PtTask task) {
        return new PtTaskSettingsResponse(
                task.getTaskType(),
                task.getTargetJobRole(),
                task.requiresTrackSelection(),
                task.getAllowedTracks(),
                task.getPreparationMinutesOptions(),
                task.getPresentationSeconds(),
                NOTICE
        );
    }

    public static PtTaskSettingsResponse legacy() {
        return new PtTaskSettingsResponse(
                PtTaskType.SERVICE_IMPROVEMENT,
                null,
                true,
                List.of(PtTrack.values()),
                PtInterviewRules.PREPARATION_MINUTES,
                PtInterviewRules.PRESENTATION_SECONDS,
                NOTICE
        );
    }
}