package com.benly.feedback.event;

import com.benly.feedback.service.FeedbackCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FeedbackScoringEventListener {

    private final FeedbackCommandService feedbackCommandService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoringRequested(ScoringRequestedEvent event) {
        feedbackCommandService.score(event.sessionId(), event.attempt());
    }
}
