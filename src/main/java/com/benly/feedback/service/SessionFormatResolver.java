package com.benly.feedback.service;

import com.benly.feedback.entity.SessionFormat;
import com.benly.pt.repository.PtSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionFormatResolver {

    private final PtSessionRepository ptSessionRepository;

    public SessionFormat resolve(Long sessionId) {
        return ptSessionRepository.existsById(sessionId) ? SessionFormat.PT : SessionFormat.QA;
    }
}
