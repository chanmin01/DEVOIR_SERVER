package com.benly.pt.dto;

import com.benly.pt.entity.PtNextAction;
import com.benly.pt.entity.PtPhase;
import com.benly.session.entity.SessionStatus;

public record PtInterviewProgressResponse(
        Long sessionId,
        SessionStatus status,
        PtPhase phase,
        int answeredCount,
        int totalQuestionCount,
        boolean processing,
        PtQuestionResponse question,
        PtNextAction nextAction) {}