package com.benly.pt.service;

import com.benly.pt.dto.PtAnswerResponse;
import com.benly.pt.dto.PtProgressResponse;

import java.time.Instant;
import java.util.UUID;

public record PtAudioPlan(
        UUID operationToken,
        Instant admittedAt,
        PtProgressResponse presentationReplay,
        PtAnswerResponse answerReplay) {}