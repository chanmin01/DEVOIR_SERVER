package com.benly.pt.exception;

import com.benly.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PtErrorCode implements ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "PT 요청 값이 올바르지 않습니다."),
    USER_UNAVAILABLE(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "기업을 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "PT 세션을 찾을 수 없습니다."),
    TASK_NOT_AVAILABLE(HttpStatus.CONFLICT, "출제 가능한 PT 과제가 없습니다."),
    REQUEST_CONFLICT(HttpStatus.CONFLICT, "같은 요청 ID가 다른 조건으로 사용되었습니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "요청이 처리 중입니다. 같은 요청 ID로 다시 시도해주세요."),
    SESSION_EXPIRED(HttpStatus.GONE, "게스트 PT 세션이 만료되었습니다."),

    INVALID_PHASE(HttpStatus.CONFLICT, "현재 PT 단계에서는 처리할 수 없습니다."),
    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 PT 면접이 아닙니다."),
    TRACK_REQUIRED(HttpStatus.CONFLICT, "발표 분야를 먼저 선택해주세요."),
    PRESENTATION_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "발표는 이미 제출되었습니다."),
    PRESENTATION_SUBMISSION_EXPIRED(HttpStatus.CONFLICT, "발표 제출 가능 시간이 지났습니다."),
    CANNOT_CANCEL(HttpStatus.CONFLICT, "현재 상태에서는 PT 면접을 취소할 수 없습니다.");


    private final HttpStatus status;
    private final String message;
}