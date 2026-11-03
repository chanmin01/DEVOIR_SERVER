package com.benly.feedback.event;

import java.time.LocalDateTime;

public record ScoringRequestedEvent(
        Long sessionId,
        LocalDateTime attempt
) {}
