package com.benly.pt.dto;

import com.benly.pt.entity.PtTrack;
import jakarta.validation.constraints.NotNull;

public record PtTrackRequest(
        @NotNull(message = "발표 분야는 필수입니다.")
        PtTrack track
) {
}