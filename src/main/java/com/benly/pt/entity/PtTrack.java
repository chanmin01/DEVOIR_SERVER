package com.benly.pt.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PtTrack {

    SECURITY("보안"),
    UI_UX("UI/UX"),
    AI("AI"),
    DATA("데이터");

    private final String label;
}