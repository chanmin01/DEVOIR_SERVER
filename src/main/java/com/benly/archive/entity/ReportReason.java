package com.benly.archive.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportReason {

    FALSE_INFO("허위 정보"),
    AD("광고, 홍보"),
    INAPPROPRIATE("부적절한 내용"),
    COPIED("다른 사이트에서 복사"),
    NDA_VIOLATION("비밀유지 위반");

    private final String label;
}
