package com.benly.auth.jwt;

import com.benly.user.entity.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String CLAIM_USER_TYPE = "type";

    private final String secret;
    private final long accessTokenValidity;
    private final long refreshTokenValidity;
    private final long guestTokenValidity;

    private SecretKey key;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long accessTokenValidity,
            @Value("${jwt.refresh-token-validity}") long refreshTokenValidity,
            @Value("${jwt.guest-token-validity}") long guestTokenValidity
    ) {
        this.secret = secret;
        this.accessTokenValidity = accessTokenValidity;
        this.refreshTokenValidity = refreshTokenValidity;
        this.guestTokenValidity = guestTokenValidity;
    }

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 회원 액세스 토큰 (기존 호출부 시그니처 유지) */
    public String createAccessToken(Long userId) {
        return createToken(userId, UserType.MEMBER, accessTokenValidity);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, UserType.MEMBER, refreshTokenValidity);
    }

    /** 비로그인 체험용 게스트 토큰 (refresh 미발급) */
    public String createGuestToken(Long userId) {
        return createToken(userId, UserType.GUEST, guestTokenValidity);
    }

    private String createToken(Long userId, UserType userType, long validity) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validity);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_TYPE, userType.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parse(token).getPayload().getSubject());
    }

    /**
     * 토큰의 사용자 유형.
     * type claim이 없는 기존 발급 토큰은 MEMBER로 간주한다(하위 호환).
     */
    public UserType getUserType(String token) {
        String type = parse(token).getPayload().get(CLAIM_USER_TYPE, String.class);
        if (type == null) {
            return UserType.MEMBER;
        }
        try {
            return UserType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return UserType.MEMBER;
        }
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw e;
        } catch (Exception e) {
            return false; // 그 외의 변조/손상된 토큰은 false 처리
        }
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public LocalDateTime getRefreshTokenExpiry() {
        return LocalDateTime.now().plus(refreshTokenValidity, java.time.temporal.ChronoUnit.MILLIS);
    }
}