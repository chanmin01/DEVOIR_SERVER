package com.benly.feedback.event;

import com.benly.feedback.service.CardScoringService;
import com.benly.feedback.service.FeedbackCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 면접 중 미리 채점. 답변이 커밋된 뒤 별도 스레드에서 카드 한 장을 채점한다.
 * 여기서 실패해도 면접 진행에는 영향이 없고, 빠진 카드는 최종 채점에서 다시 채점한다.
 * 카드가 끝났을 때 최종 채점이 대기 시간 초과로 실패해 있었다면 이어서 마무리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreScoringEventListener {

    private final CardScoringService cardScoringService;
    private final FeedbackCommandService feedbackCommandService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTopicFinished(TopicFinishedEvent event) {
        try {
            if (cardScoringService.scoreTopic(event.mainQuestionId())) {
                feedbackCommandService.resumeIfReady(event.sessionId());
            }
        } catch (Exception e) {
            log.error("미리 채점 실패 sessionId={} mainQuestionId={}",
                    event.sessionId(), event.mainQuestionId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPresentationSubmitted(PresentationSubmittedEvent event) {
        try {
            if (cardScoringService.scorePresentation(event.sessionId())) {
                feedbackCommandService.resumeIfReady(event.sessionId());
            }
        } catch (Exception e) {
            log.error("발표 미리 채점 실패 sessionId={}", event.sessionId(), e);
        }
    }
}
