package com.benly.question.exception;

import com.benly.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnswerErrorCode implements ErrorCode {
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다."),
    ANSWER_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 질문에 접근할 수 없습니다."),
    SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 면접이 아닙니다."),
    ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답변한 질문입니다."),
    ANSWER_TOO_SHORT(HttpStatus.UNPROCESSABLE_CONTENT, "답변이 너무 짧아 평가가 어려워요."),
    QUESTION_SESSION_MISMATCH(HttpStatus.BAD_REQUEST, "요청한 세션의 질문이 아닙니다."),
    INVALID_SEQUENCE(HttpStatus.BAD_REQUEST,  "현재 진행 순서가 아닌 질문입니다."),
    TTS_FAILED(HttpStatus.BAD_GATEWAY, "질문 음성을 만들지 못했어요. 화면의 질문을 읽고 답변해 주세요.");

    private final HttpStatus status;
    private final String message;
}
