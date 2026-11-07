package com.benly.pt.entity;

public enum PtOperationKind {
    GENERATE_QUESTION(90),
    PRESENTATION_AUDIO(180),
    ANSWER_AUDIO(180);

    private final int leaseSeconds;

    PtOperationKind(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int leaseSeconds() {
        return leaseSeconds;
    }
}