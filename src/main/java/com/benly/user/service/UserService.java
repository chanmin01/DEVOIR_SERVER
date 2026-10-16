package com.benly.user.service;

import com.benly.auth.repository.RefreshTokenRepository;
import com.benly.document.entity.Document;
import com.benly.document.repository.DocumentRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.dto.UserMeResponse;
import com.benly.user.entity.User;
import com.benly.user.event.UserWithdrawnEvent;
import com.benly.user.exception.UserErrorCode;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void updateNickname(Long userId, String nickname) {
        User user = getActiveUser(userId);
        user.changeNickname(nickname);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (user.isDeleted()) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String kakaoId = user.getKakaoId();

        List<Document> docs = documentRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<String> fileKeys = docs.stream().map(Document::getStorageKey).toList();
        docs.forEach(Document::softDelete);

        user.withdraw();
        refreshTokenRepository.deleteByUserId(userId);

        eventPublisher.publishEvent(new UserWithdrawnEvent(kakaoId, fileKeys));
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    public UserMeResponse getMyInfo(Long userId) {
        User user = getActiveUser(userId);
        return UserMeResponse.from(user);
    }
}