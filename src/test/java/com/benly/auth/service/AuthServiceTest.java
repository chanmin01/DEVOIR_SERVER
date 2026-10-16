package com.benly.auth.service;

import com.benly.auth.client.KakaoOAuthClient;
import com.benly.auth.client.dto.KakaoUserInfo;
import com.benly.auth.dto.KakaoLoginRequest;
import com.benly.auth.dto.KakaoLoginResponse;
import com.benly.auth.entity.RefreshToken;
import com.benly.auth.exception.AuthErrorCode;
import com.benly.auth.jwt.JwtProvider;
import com.benly.auth.repository.RefreshTokenRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private UserRegistrationService userRegistrationService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("신규 유저가 약관 동의하면 가입되고 토큰이 발급된다")
    void newUserLogin() {
        // given
        User newUser = User.of("12345", "홍길동");
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        given(userRepository.findByKakaoId("12345")).willReturn(Optional.empty());
        given(userRegistrationService.register(any(KakaoUserInfo.class), anyBoolean()))
                .willReturn(newUser);
        given(userRepository.findByIdForUpdate(any()))
                .willReturn(Optional.of(newUser));   // 로그인 시 유저 행 잠금
        given(jwtProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiry())
                .willReturn(LocalDateTime.now().plusWeeks(2));

        KakaoLoginRequest request = new KakaoLoginRequest("code", true);

        // when
        KakaoLoginResponse response = authService.kakaoLogin(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.isNewUser()).isTrue();
    }

    @Test
    @DisplayName("신규 유저가 약관 미동의하면 예외가 발생한다")
    void newUserWithoutTermsAgreed() {
        // given
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        given(userRepository.findByKakaoId("12345")).willReturn(Optional.empty());
        given(userRegistrationService.register(any(KakaoUserInfo.class), anyBoolean()))
                .willThrow(new BusinessException(AuthErrorCode.TERMS_NOT_AGREED));

        KakaoLoginRequest request = new KakaoLoginRequest("code", false);

        // when & then
        assertThatThrownBy(() -> authService.kakaoLogin(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("동시 가입 충돌 시 기존 유저로 복구하고 isNewUser는 false다")
    void concurrentRegisterFallsBackToExisting() {
        // given
        User existing = User.of("12345", "홍길동");
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        // 첫 조회: 없음(신규처럼 진입) → 저장 실패 후 재조회: 있음(기존 복구)
        given(userRepository.findByKakaoId("12345"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(existing));
        given(userRegistrationService.register(any(KakaoUserInfo.class), anyBoolean()))
                .willThrow(new DataIntegrityViolationException("duplicate kakao_id"));
        given(userRepository.findByIdForUpdate(any()))
                .willReturn(Optional.of(existing));   // 로그인 시 유저 행 잠금
        given(jwtProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(LocalDateTime.now().plusWeeks(2));

        // when
        KakaoLoginResponse response = authService.kakaoLogin(new KakaoLoginRequest("code", true));

        // then
        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 후 같은 카카오 계정으로 로그인하면 신규 가입되고 isNewUser는 true다")
    void withdrawnUserReloginRegistersAsNew() {
        // given: 탈퇴 시 kakaoId가 바뀌어 기존 계정이 조회되지 않음
        User newUser = User.of("12345", "홍길동");
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        given(userRepository.findByKakaoId("12345")).willReturn(Optional.empty());
        given(userRegistrationService.register(any(KakaoUserInfo.class), anyBoolean()))
                .willReturn(newUser);
        given(userRepository.findByIdForUpdate(any())).willReturn(Optional.of(newUser));
        given(jwtProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(LocalDateTime.now().plusWeeks(2));

        // when
        KakaoLoginResponse response = authService.kakaoLogin(new KakaoLoginRequest("code", true));

        // then
        assertThat(response.isNewUser()).isTrue();
        verify(userRegistrationService).register(any(KakaoUserInfo.class), anyBoolean());
    }

    @Test
    @DisplayName("기존 토큰이 없으면 유저 행을 잠근 뒤 새 토큰을 저장한다")
    void loginAcquiresLockAndSavesTokenWhenNoneExists() {
        // given
        User user = User.of("12345", "홍길동");
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        given(userRepository.findByKakaoId("12345")).willReturn(Optional.of(user));
        given(userRepository.findByIdForUpdate(any())).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserId(any())).willReturn(Optional.empty()); // 기존 토큰 없음
        given(jwtProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh-token");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(LocalDateTime.now().plusWeeks(2));

        // when
        authService.kakaoLogin(new KakaoLoginRequest("code", true));

        // then — 락 조회 + 기존 토큰 조회 후 새로 저장
        verify(userRepository).findByIdForUpdate(user.getId());
        verify(refreshTokenRepository).findByUserId(user.getId());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("재로그인 시 기존 토큰을 갱신하고 새로 INSERT하지 않는다")
    void loginUpdatesExistingTokenOnRelogin() {
        // given
        User user = User.of("12345", "홍길동");
        RefreshToken existingToken =
                RefreshToken.of(user, "old-refresh", LocalDateTime.now().plusWeeks(2));
        given(kakaoOAuthClient.getKakaoUser("code"))
                .willReturn(new KakaoUserInfo("12345", "홍길동"));
        given(userRepository.findByKakaoId("12345")).willReturn(Optional.of(user));
        given(userRepository.findByIdForUpdate(any())).willReturn(Optional.of(user));
        given(refreshTokenRepository.findByUserId(any()))
                .willReturn(Optional.of(existingToken));   // 기존 토큰 있음
        given(jwtProvider.createAccessToken(any())).willReturn("access-token");
        given(jwtProvider.createRefreshToken(any())).willReturn("new-refresh");
        given(jwtProvider.getRefreshTokenExpiry()).willReturn(LocalDateTime.now().plusWeeks(2));

        // when
        authService.kakaoLogin(new KakaoLoginRequest("code", true));

        // then — 기존 토큰이 갱신되고, 새 INSERT(save)는 없어야 함 (중복키 500 회귀 방지)
        assertThat(existingToken.getRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 유저의 토큰 재발급 요청은 거부되고 새 토큰이 저장되지 않는다")
    void reissueRejectsWithdrawnUser() {
        // given
        User withdrawnUser = User.of("12345", "홍길동");
        withdrawnUser.softDelete();
        RefreshToken saved = RefreshToken.of(withdrawnUser, "refresh-token",
                LocalDateTime.now().plusWeeks(2));

        given(jwtProvider.isValid("refresh-token")).willReturn(true);
        given(refreshTokenRepository.findByRefreshToken("refresh-token"))
                .willReturn(Optional.of(saved));
        given(userRepository.findByIdForUpdate(any()))
                .willReturn(Optional.of(withdrawnUser));

        // when & then
        assertThatThrownBy(() -> authService.reissue("refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_TOKEN);

        verify(refreshTokenRepository).delete(saved);
        verify(refreshTokenRepository, never()).save(any());
    }
}