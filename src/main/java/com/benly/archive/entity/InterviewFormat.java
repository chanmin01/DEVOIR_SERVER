package com.benly.archive.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewFormat {

    QA("일반 면접"),
    PT("PT 면접");

    private final String label;
}
