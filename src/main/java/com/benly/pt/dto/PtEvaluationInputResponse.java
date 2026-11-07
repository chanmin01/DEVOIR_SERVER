package com.benly.pt.dto;

import com.benly.pt.entity.PtTrack;
import com.benly.question.entity.AnswerType;

import java.time.Instant;
import java.util.List;

public record PtEvaluationInputResponse(
        int schemaVersion,
        Long sessionId,
        String companyName,
        String jobRole,
        PtTrack track,
        PtTaskResponse task,
        Presentation presentation,
        List<Turn> turns) {
    public record Presentation(
            String transcript, AnswerType inputType, Integer durationSec, Instant submittedAt) {}

    public record Turn(
            PtQuestionResponse question,
            Long answerId,
            String transcript,
            AnswerType inputType,
            Integer durationSec) {}
}