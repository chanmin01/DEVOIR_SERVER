package com.benly.feedback.event;

public record TopicFinishedEvent(
        Long sessionId,
        Long mainQuestionId
) {}
