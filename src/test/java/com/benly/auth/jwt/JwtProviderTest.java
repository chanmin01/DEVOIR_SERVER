package com.benly.auth.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-secret-key-for-jwt-must-be-at-least-32-bytes-long",
                3600000L,
                1209600000L,
                21600000L
        );
        jwtProvider.init();
    }

    @Test
    @DisplayName("access 토큰을 발급하고 검증하면 userId가 복원된다")
    void createAndParseAccessToken() {
        // given
        Long userId = 1L;

        // when
        String token = jwtProvider.createAccessToken(userId);
        Long extracted = jwtProvider.getUserId(token);

        // then
        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    @DisplayName("유효한 토큰은 isValid가 true를 반환한다")
    void validToken() {
        // given
        String token = jwtProvider.createAccessToken(1L);

        // when
        boolean result = jwtProvider.isValid(token);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이상한 문자열은 isValid가 false를 반환한다")
    void invalidToken() {
        // when
        boolean result = jwtProvider.isValid("이건-토큰이-아님");

        // then
        assertThat(result).isFalse();
    }
}
