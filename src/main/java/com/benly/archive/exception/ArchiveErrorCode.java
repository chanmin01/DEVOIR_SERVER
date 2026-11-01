package com.benly.archive.exception;

import com.benly.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ArchiveErrorCode implements ErrorCode {

    COMPANY_INPUT_REQUIRED(HttpStatus.BAD_REQUEST, "회사를 선택하거나 입력해 주세요."),
    COMPANY_INPUT_CONFLICT(HttpStatus.BAD_REQUEST, "회사는 선택과 직접 입력 중 하나만 가능합니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "검색어는 2자 이상 입력해 주세요."),
    PT_ONLY_TECHNICAL(HttpStatus.BAD_REQUEST, "PT 면접은 기술 단계만 등록할 수 있습니다."),
    FUTURE_INTERVIEW_MONTH(HttpStatus.BAD_REQUEST, "미래의 면접 시기는 입력할 수 없습니다."),
    DUPLICATE_QUESTION_IN_REQUEST(HttpStatus.BAD_REQUEST, "같은 질문이 중복되어 있습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "잘못된 커서입니다."),
    CANNOT_REPORT_OWN_QUESTION(HttpStatus.BAD_REQUEST, "내가 등록한 면접 기록은 신고할 수 없습니다."),
    SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요."),
    SEARCH_KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "검색어는 50자까지 입력할 수 있습니다."),
    INVALID_SEARCH_SIZE(HttpStatus.BAD_REQUEST, "size는 1~20 사이여야 합니다."),
    COMPANY_NAME_TOO_SHORT(HttpStatus.BAD_REQUEST, "회사명을 2자 이상 입력해 주세요."),
    INVALID_INTERVIEW_MONTH(HttpStatus.BAD_REQUEST, "면접 시기를 다시 확인해 주세요."),
    QUESTION_TOO_SHORT(HttpStatus.BAD_REQUEST, "질문은 5자 이상 입력해 주세요."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "size는 1~50 사이여야 합니다."),
    INVALID_COMPANY_ID(HttpStatus.BAD_REQUEST, "잘못된 회사 ID입니다."),
    INVALID_PATH(HttpStatus.BAD_REQUEST, "잘못된 요청 경로입니다."),

    ARCHIVE_FORBIDDEN(HttpStatus.FORBIDDEN, "다른 사용자가 등록한 질문은 삭제할 수 없습니다."),

    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회사입니다."),
    ARCHIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 등록 건입니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 질문입니다."),

    DUPLICATE_SUBMISSION(HttpStatus.CONFLICT, "이미 등록된 질문이에요."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 면접 기록이에요."),

    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "하루에 질문 20개까지 등록할 수 있어요.");

    private final HttpStatus status;
    private final String message;
}
