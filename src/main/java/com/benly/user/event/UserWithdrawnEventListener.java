package com.benly.user.event;

import com.benly.auth.client.KakaoOAuthClient;
import com.benly.document.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final S3StorageService s3StorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserWithdrawnEvent event) {
        try {
            kakaoOAuthClient.unlink(event.kakaoId());
        } catch (Exception e) {
            log.warn("[WITHDRAW] 카카오 unlink 실패 kakaoId={}", event.kakaoId(), e);
        }

        for (String key : event.fileKeys()) {
            try {
                s3StorageService.delete(key);
            } catch (Exception e) {
                log.warn("[WITHDRAW] S3 삭제 실패 key={}", key, e);
            }
        }
    }
}