package com.benly.pt.dto;

import com.benly.pt.entity.PtPhase;
import com.benly.pt.entity.PtSession;
import com.benly.pt.entity.PtTrack;
import com.benly.session.entity.SessionStatus;

import java.time.Instant;

public record PtProgressResponse(
        Long sessionId,
        SessionStatus status,
        PtPhase phase,
        PtTrack track,
        Instant preparationDeadline,
        Instant presentationStartedAt,
        Instant presentationDeadline,
        Instant serverTime,
        PtPresentationResponse presentation,
        String nextAction
) {

    public static PtProgressResponse from(
            PtSession detail,
            Instant now
    ) {
        var session = detail.getSession();

        PtPhase phase = session.getStatus() == SessionStatus.CANCELED
                ? PtPhase.CANCELED
                : detail.getPhase();

        PtPresentationResponse presentation =
                detail.getPresentationTranscript() == null
                        ? null
                        : new PtPresentationResponse(
                        detail.getPresentationTranscript(),
                        detail.getPresentationInputType(),
                        detail.getPresentationSubmittedAt()
                );

        String nextAction = switch (phase) {
            case PREPARING -> detail.getTrack() == null
                    ? "SELECT_TRACK"
                    : "START_PRESENTATION";
            case PRESENTING -> "SUBMIT_PRESENTATION";
            case GENERATING -> "GENERATE_QUESTION";
            case QUESTIONING -> "ANSWER";
            case COMPLETED -> "FINISH";
            case CANCELED -> "NONE";
        };

        return new PtProgressResponse(
                session.getId(),
                session.getStatus(),
                phase,
                detail.getTrack(),
                detail.getPreparationDeadline(),
                detail.getPresentationStartedAt(),
                detail.getPresentationDeadline(),
                now,
                presentation,
                nextAction
        );
    }
}