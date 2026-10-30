package com.benly.archive.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyNameNormalizerTest {

    @Test
    @DisplayName("주식회사 표기와 공백, 대소문자가 달라도 같은 값으로 바뀐다")
    void sameCompanyDifferentNotation() {
        assertThat(CompanyNameNormalizer.normalize("(주)카카오")).isEqualTo("카카오");
        assertThat(CompanyNameNormalizer.normalize("㈜ 카카오")).isEqualTo("카카오");
        assertThat(CompanyNameNormalizer.normalize("주식회사 카카오")).isEqualTo("카카오");
        assertThat(CompanyNameNormalizer.normalize("  카카오  ")).isEqualTo("카카오");
    }

    @Test
    @DisplayName("영문은 소문자로 바꾸고 특수문자는 지운다")
    void lowercaseAndRemoveSymbols() {
        assertThat(CompanyNameNormalizer.normalize("LG CNS")).isEqualTo("lgcns");
        assertThat(CompanyNameNormalizer.normalize("KAKAO!")).isEqualTo("kakao");
        assertThat(CompanyNameNormalizer.normalize("SK하이닉스")).isEqualTo("sk하이닉스");
    }

    @Test
    @DisplayName("null이면 빈 문자열을 돌려준다")
    void nullToEmpty() {
        assertThat(CompanyNameNormalizer.normalize(null)).isEmpty();
    }
}
