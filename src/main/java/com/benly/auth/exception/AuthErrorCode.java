package com.benly.auth.exception;

import com.benly.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "약관 동의가 필요합니다."),
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다. 다시 로그인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    GUEST_ISSUE_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
