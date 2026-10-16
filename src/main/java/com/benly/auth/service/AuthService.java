package com.benly.auth.service;

import com.benly.auth.client.KakaoOAuthClient;
import com.benly.auth.client.dto.KakaoUserInfo;
import com.benly.auth.dto.KakaoLoginRequest;
import com.benly.auth.dto.KakaoLoginResponse;
import com.benly.auth.dto.TokenPair;
import com.benly.auth.dto.UserResolution;
import com.benly.auth.entity.RefreshToken;
import com.benly.auth.exception.AuthErrorCode;
import com.benly.auth.jwt.JwtProvider;
import com.benly.auth.repository.RefreshTokenRepository;
import com.benly.global.exception.BusinessException;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final UserRegistrationService userRegistrationService;

    @Transactional
    public KakaoLoginResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserInfo kakaoUser = kakaoOAuthClient.getKakaoUser(request.authorizationCode());

        UserResolution resolution = findOrRegister(kakaoUser, request.termsAgreed());

        userRepository.findByIdForUpdate(resolution.user().getId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED));

        TokenPair tokens = issueAndSaveTokens(resolution.user());

        return KakaoLoginResponse.of(tokens, resolution.user(), resolution.isNewUser());
    }

    private UserResolution findOrRegister(KakaoUserInfo kakaoUser, boolean termsAgreed) {
        return userRepository.findByKakaoId(kakaoUser.kakaoId())
                .map(this::resolveExisting)
                .orElseGet(() -> registerOrFindExisting(kakaoUser, termsAgreed));
    }

    private UserResolution resolveExisting(User user) {
        return new UserResolution(user, false);
    }

    private UserResolution registerOrFindExisting(KakaoUserInfo kakaoUser, boolean termsAgreed) {
        try {
            User created = userRegistrationService.register(kakaoUser, termsAgreed);
            return new UserResolution(created, true);
        } catch (DataIntegrityViolationException e) {
            User existing = userRepository.findByKakaoId(kakaoUser.kakaoId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.KAKAO_AUTH_FAILED));
            return new UserResolution(existing, false);
        }
    }

    @Transactional
    public TokenPair reissue(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        RefreshToken saved = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));

        User user = userRepository.findByIdForUpdate(saved.getUser().getId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));

        if (user.isDeleted()) {
            refreshTokenRepository.delete(saved);
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        return issueAndSaveTokens(user);
    }

    private TokenPair issueAndSaveTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        token -> token.update(refreshToken, jwtProvider.getRefreshTokenExpiry()),
                        () -> refreshTokenRepository.save(
                                RefreshToken.of(user, refreshToken, jwtProvider.getRefreshTokenExpiry())
                        )
                );

        return new TokenPair(accessToken, refreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
