package com.benly.auth.controller;

import com.benly.auth.dto.GuestLoginResponse;
import com.benly.auth.dto.KakaoLoginRequest;
import com.benly.auth.dto.KakaoLoginResponse;
import com.benly.auth.dto.TokenPair;
import com.benly.auth.dto.TokenRefreshRequest;
import com.benly.auth.jwt.JwtProvider;
import com.benly.auth.service.AuthService;
import com.benly.auth.service.GuestAuthService;
import com.benly.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GuestAuthService guestAuthService;
    private final JwtProvider jwtProvider;

    @PostMapping("/kakao/login")
    public ApiResponse<KakaoLoginResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.kakaoLogin(request));
    }

    /** 비로그인 체험 시작 — 게스트 계정 생성 및 토큰 발급 */
    @PostMapping("/guest")
    public ApiResponse<GuestLoginResponse> issueGuestToken(HttpServletRequest request) {
        return ApiResponse.success("게스트로 시작합니다.",
                guestAuthService.issueGuestToken(clientIp(request)));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<TokenPair> reissue(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ApiResponse.success("로그아웃되었습니다.");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // ALB(append 모드)는 자신이 본 클라이언트 IP를 맨 뒤에 덧붙인다.
            // 앞쪽 값은 클라이언트가 임의로 넣을 수 있어 레이트리밋 우회에 쓰인다.
            String[] hops = forwarded.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (!nearest.isEmpty()) {
                return nearest;
            }
        }
        return request.getRemoteAddr();
    }
}