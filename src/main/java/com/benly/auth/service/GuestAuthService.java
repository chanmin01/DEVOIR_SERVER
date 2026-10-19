package com.benly.auth.service;

import com.benly.auth.dto.GuestLoginResponse;
import com.benly.auth.exception.AuthErrorCode;
import com.benly.auth.jwt.JwtProvider;
import com.benly.global.exception.BusinessException;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuestAuthService {

    private static final Duration COOLDOWN = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    // TODO: 서버가 여러 대가 되면 인스턴스별로 따로 동작하므로 Bucket4j 등으로 교체
    private final Map<String, Instant> recentIssues = new ConcurrentHashMap<>();

    /**
     * 비로그인 체험용 게스트 계정을 만들고 토큰을 발급한다.
     * 게스트도 users 레코드를 가지므로, 이후 세션·답변·채점 API는
     * 회원과 완전히 동일한 경로로 동작한다.
     */
    @Transactional
    public GuestLoginResponse issueGuestToken(String clientIp) {
        rejectIfTooFrequent(clientIp);

        User guest = userRepository.save(User.createGuest());
        String accessToken = jwtProvider.createGuestToken(guest.getId());

        log.info("게스트 발급. userId={}, ip={}", guest.getId(), clientIp);
        return GuestLoginResponse.of(guest.getId(), accessToken, guest.getNickname());
    }

    private void rejectIfTooFrequent(String clientIp) {
        Instant now = Instant.now();

        // 만료분 정리 (맵 무한 증가 방지)
        recentIssues.entrySet().removeIf(e -> e.getValue().plus(COOLDOWN).isBefore(now));

        Instant last = recentIssues.get(clientIp);
        if (last != null && last.plus(COOLDOWN).isAfter(now)) {
            throw new BusinessException(AuthErrorCode.GUEST_ISSUE_TOO_FREQUENT);
        }
        recentIssues.put(clientIp, now);
    }
}