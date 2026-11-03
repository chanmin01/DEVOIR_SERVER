package com.benly.global.recovery;

import com.benly.feedback.repository.SessionFeedbackRepository;
import com.benly.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaleTaskRecovery {

    private final SessionFeedbackRepository sessionFeedbackRepository;
    private final SessionRepository sessionRepository;
    private final StaleTaskPolicy staleTaskPolicy;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        int scoring = sessionFeedbackRepository.failStaleScoring(staleTaskPolicy.scoringThreshold());
        int generating = sessionRepository.failStaleGenerating(
                staleTaskPolicy.generationThreshold(), staleTaskPolicy.generationQueueThreshold());
        if (scoring > 0 || generating > 0) {
            log.warn("멈춘 작업 정리 채점={}건 질문생성={}건", scoring, generating);
        }
    }
}
