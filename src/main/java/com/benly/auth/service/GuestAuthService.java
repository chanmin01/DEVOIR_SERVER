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

        // 확인과 기록을 하나의 원자적 연산으로 묶는다.
        // get 후 put 으로 나누면 같은 IP의 동시 요청이 둘 다 last == null 을 읽고 통과한다.
        Instant kept = recentIssues.merge(clientIp, now,
                (last, candidate) -> last.plus(COOLDOWN).isAfter(candidate) ? last : candidate);

        // merge 가 now 를 그대로 돌려줬다면 이번 요청이 기록된 것, 아니면 쿨다운에 걸린 것
        if (kept != now) {
            throw new BusinessException(AuthErrorCode.GUEST_ISSUE_TOO_FREQUENT);
        }
    }
}