package com.benly.pt.service;

import com.benly.pt.entity.PtSession;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.event.SessionCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PtSessionCancellationListener {

    private final PtSessionRepository details;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCanceled(SessionCanceledEvent event) {
        details.findById(event.sessionId())
                .ifPresent(PtSession::cancel);
    }
}