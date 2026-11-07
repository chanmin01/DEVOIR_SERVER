package com.benly.feedback.dto;

import com.benly.feedback.dto.ScoringResult.MainQuestionInput;
import com.benly.feedback.dto.ScoringResult.PresentationInput;
import com.benly.feedback.entity.Axis;
import com.benly.feedback.entity.AxisSet;
import com.benly.feedback.entity.SessionFormat;

import java.util.List;

public record ScoringPlan(
        List<Axis> axes,
        ScoringContext ctx,
        AxisSet axisSet,
        List<MainInput> mains,
        SessionFormat format,
        PresentationInput presentation   // PT만, QA는 null
) {
    /** 발표가 없는 QA용 */
    public ScoringPlan(List<Axis> axes, ScoringContext ctx, AxisSet axisSet,
                       List<MainInput> mains, SessionFormat format) {
        this(axes, ctx, axisSet, mains, format, null);
    }

    public record MainInput(Long mainQuestionId, MainQuestionInput input) {
    }
}
