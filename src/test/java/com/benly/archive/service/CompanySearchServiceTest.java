package com.benly.archive.service;

import com.benly.archive.dto.CompanyCandidate;
import com.benly.archive.dto.CompanySearchResponse;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyAliasRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.benly.archive.entity.CompanySource.DART;
import static com.benly.archive.entity.CompanySource.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CompanySearchServiceTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyAliasRepository companyAliasRepository;

    @InjectMocks
    private CompanySearchService companySearchService;

    @Test
    @DisplayName("검색어가 비어 있으면 400")
    void blankKeyword() {
        assertThatThrownBy(() -> companySearchService.search("   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.SEARCH_KEYWORD_REQUIRED);
    }

    @Test
    @DisplayName("정규화 후 2자 미만이면 400")
    void tooShortAfterNormalize() {
        assertThatThrownBy(() -> companySearchService.search("(주)카", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.SEARCH_KEYWORD_TOO_SHORT);
    }

    @Test
    @DisplayName("size가 1~20 밖이면 400")
    void invalidSize() {
        assertThatThrownBy(() -> companySearchService.search("카카", 0))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_SEARCH_SIZE);
        assertThatThrownBy(() -> companySearchService.search("카카", 21))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_SEARCH_SIZE);
    }

    @Test
    @DisplayName("이름과 별칭에 같은 회사가 걸리면 하나로 합치고 이름 매칭을 쓴다")
    void mergeSameCompany() {
        given(companyRepository.searchByName(eq("카카"), any()))
                .willReturn(List.of(new CompanyCandidate(3L, "카카오", DART, "카카오", 34L)));
        given(companyAliasRepository.searchByAlias(eq("카카"), any()))
                .willReturn(List.of(new CompanyCandidate(3L, "카카오", DART, "카카오톡", "카카오톡", 34L)));

        CompanySearchResponse result = companySearchService.search("카카", null);

        assertThat(result.companies()).hasSize(1);
        assertThat(result.companies().get(0).matchedAlias()).isNull();
    }

    @Test
    @DisplayName("별칭으로만 걸리면 matchedAlias를 내려준다")
    void aliasOnly() {
        given(companyRepository.searchByName(eq("kakao"), any())).willReturn(List.of());
        given(companyAliasRepository.searchByAlias(eq("kakao"), any()))
                .willReturn(List.of(new CompanyCandidate(3L, "카카오", DART, "kakao", "kakao", 34L)));

        CompanySearchResponse result = companySearchService.search("KAKAO", null);

        assertThat(result.companies()).hasSize(1);
        assertThat(result.companies().get(0).name()).isEqualTo("카카오");
        assertThat(result.companies().get(0).matchedAlias()).isEqualTo("kakao");
    }

    @Test
    @DisplayName("질문 0개인 직접 입력 회사는 빼고, DART 회사는 0개여도 남긴다")
    void hideEmptyUserCompany() {
        given(companyRepository.searchByName(eq("로켓"), any())).willReturn(List.of(
                new CompanyCandidate(10L, "로켓펀치", USER, "로켓펀치", 0L),
                new CompanyCandidate(11L, "로켓배송", DART, "로켓배송", 0L)));
        given(companyAliasRepository.searchByAlias(eq("로켓"), any())).willReturn(List.of());

        CompanySearchResponse result = companySearchService.search("로켓", null);

        assertThat(result.companies())
                .extracting(CompanySearchResponse.Item::name)
                .containsExactly("로켓배송");
    }

    @Test
    @DisplayName("정확히 일치하는 회사가 질문 수와 상관없이 맨 위로 온다")
    void exactMatchFirst() {
        given(companyRepository.searchByName(eq("카카오"), any())).willReturn(List.of(
                new CompanyCandidate(7L, "카카오뱅크", DART, "카카오뱅크", 50L),
                new CompanyCandidate(3L, "카카오", DART, "카카오", 2L),
                new CompanyCandidate(8L, "카카오페이", DART, "카카오페이", 10L)));
        given(companyAliasRepository.searchByAlias(eq("카카오"), any())).willReturn(List.of());

        CompanySearchResponse result = companySearchService.search("카카오", null);

        assertThat(result.companies())
                .extracting(CompanySearchResponse.Item::name)
                .containsExactly("카카오", "카카오뱅크", "카카오페이");
    }

    @Test
    @DisplayName("size만큼만 자른다")
    void limitSize() {
        given(companyRepository.searchByName(eq("카카"), any())).willReturn(List.of(
                new CompanyCandidate(3L, "카카오", DART, "카카오", 3L),
                new CompanyCandidate(7L, "카카오뱅크", DART, "카카오뱅크", 2L),
                new CompanyCandidate(8L, "카카오페이", DART, "카카오페이", 1L)));
        given(companyAliasRepository.searchByAlias(eq("카카"), any())).willReturn(List.of());

        CompanySearchResponse result = companySearchService.search("카카", 2);

        assertThat(result.companies()).hasSize(2);
    }
}