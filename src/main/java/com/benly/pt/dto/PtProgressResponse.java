package com.benly.pt.dto;

import com.benly.pt.entity.PtPhase;
import com.benly.pt.entity.PtSession;
import com.benly.pt.entity.PtTrack;
import com.benly.session.entity.SessionStatus;
import com.benly.pt.entity.PtNextAction;

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
        PtNextAction nextAction
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

        PtNextAction nextAction = switch (phase) {
            case PREPARING -> detail.getTrack() == null
                    ? PtNextAction.SELECT_TRACK
                    : PtNextAction.START_PRESENTATION;
            case PRESENTING -> PtNextAction.SUBMIT_PRESENTATION;
            case GENERATING -> PtNextAction.GENERATE_QUESTION;
            case QUESTIONING -> PtNextAction.ANSWER;
            case COMPLETED -> PtNextAction.FINISH;
            case CANCELED -> PtNextAction.NONE;
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