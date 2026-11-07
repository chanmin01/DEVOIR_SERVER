package com.benly.feedback.dto;

import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.SessionFormat;

import java.util.List;

public record ScoringPlan(
        List<Axis> axes,
        ScoringContext ctx,
        AxisSet axisSet,
        List<MainInput> mains,
        SessionFormat format
) {
    public record MainInput(Long mainQuestionId, MainQuestionInput input) {
    }
}
