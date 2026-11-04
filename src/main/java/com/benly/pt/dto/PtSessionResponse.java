package com.benly.pt.dto;

import com.benly.archive.entity.InterviewFormat;
import com.benly.pt.entity.PtPhase;
import com.benly.pt.entity.PtTrack;
import com.benly.session.entity.SessionStatus;

import java.time.Instant;

public record PtSessionResponse(
        Long sessionId,
        InterviewFormat format,
        SessionStatus status,
        PtPhase phase,
        String jobRole,
        PtTaskResponse task,
        int preparationMinutes,
        Instant preparationStartedAt,
        Instant preparationDeadline,
        Instant serverTime,
        Instant expiresAt,
        PtTrack track,
        Instant presentationStartedAt,
        Instant presentationDeadline,
        PtPresentationResponse presentation,
        String nextAction
) {
}