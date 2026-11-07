package com.benly.feedback.service;

import com.benly.feedback.entity.SessionFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TotalScoreCalculatorTest {

    @Test
    @DisplayName("QA는 질문 카드 평균이고 구성은 없다")
    void qaAverage() {
        TotalScoreCalculator.Result result =
                TotalScoreCalculator.calculate(SessionFormat.QA, null, List.of(80, 70, 90));

        assertThat(result.total()).isEqualTo(80);
        assertThat(result.breakdown()).isNull();
    }

    @Test
    @DisplayName("PT는 발표 30, 질의응답 평균 70으로 계산한다")
    void ptWeighted() {
        TotalScoreCalculator.Result result =
                TotalScoreCalculator.calculate(SessionFormat.PT, 70, List.of(77, 75));

        // 70 * 0.3 + 76 * 0.7 = 74.2
        assertThat(result.total()).isEqualTo(74);
        assertThat(result.breakdown().presentation()).isEqualTo(70);
        assertThat(result.breakdown().qaAverage()).isEqualTo(76);
        assertThat(result.breakdown().presentationWeight()).isEqualTo(30);
        assertThat(result.breakdown().qaWeight()).isEqualTo(70);
    }

    @Test
    @DisplayName("PT라도 발표 점수가 없으면 평균으로 계산한다")
    void ptWithoutPresentation() {
        TotalScoreCalculator.Result result =
                TotalScoreCalculator.calculate(SessionFormat.PT, null, List.of(77, 75));

        assertThat(result.total()).isEqualTo(76);
        assertThat(result.breakdown()).isNull();
    }
}
