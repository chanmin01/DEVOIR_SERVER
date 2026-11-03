package com.benly.pt.exception;

import com.benly.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PtErrorCode implements ErrorCode {

    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "PT 요청 값이 올바르지 않습니다."
    ),

    USER_UNAVAILABLE(
            HttpStatus.UNAUTHORIZED,
            "사용자 인증을 확인해주세요."
    ),

    COMPANY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "기업을 찾을 수 없습니다."
    ),

    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PT 세션을 찾을 수 없습니다."
    ),

    NO_TASK(
            HttpStatus.CONFLICT,
            "출제 가능한 PT 과제가 없습니다."
    ),

    REQUEST_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 요청 ID가 다른 조건으로 사용되었습니다."
    ),

    SESSION_EXPIRED(
            HttpStatus.GONE,
            "게스트 PT 세션이 만료되었습니다."
    );

    private final HttpStatus status;
    private final String message;
}