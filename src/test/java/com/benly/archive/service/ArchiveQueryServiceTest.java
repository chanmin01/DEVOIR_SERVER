package com.benly.archive.service;

import com.benly.archive.dto.ArchiveListResponse;
import com.benly.archive.dto.ArchiveSearch;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.*;
import com.benly.archive.support.ArchiveSort;
import com.benly.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ArchiveQueryServiceTest {

    @Mock private InterviewArchiveRepository archiveRepository;
    @Mock private ArchiveQuestionRepository questionRepository;
    @Mock private ArchiveLikeRepository likeRepository;
    @Mock private ArchiveReportRepository reportRepository;
    @Mock private CompanyRepository companyRepository;

    @InjectMocks
    private ArchiveQueryService archiveQueryService;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("size가 1~50 밖이면 400")
    void invalidSize() {
        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID, search(null, null, null, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_PAGE_SIZE);
        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID, search(null, null, null, 51)))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_PAGE_SIZE);
        verifyNoInteractions(archiveRepository);
    }

    @Test
    @DisplayName("companyId가 0 이하면 400")
    void invalidCompanyId() {
        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID, search(0L, null, null, null)))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_COMPANY_ID);
        verifyNoInteractions(companyRepository, archiveRepository);
    }

    @Test
    @DisplayName("없는 회사로 필터하면 404")
    void companyNotFound() {
        given(companyRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID, search(99L, null, null, null)))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.COMPANY_NOT_FOUND);
        verifyNoInteractions(archiveRepository);
    }

    @Test
    @DisplayName("인기순에 최신순 커서를 주면 400")
    void popularWithLatestCursor() {
        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID, search(null, ArchiveSort.POPULAR, "41", null)))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_CURSOR);
        verifyNoInteractions(archiveRepository);
    }

    @Test
    @DisplayName("커서의 하트 수가 int 범위를 넘으면 400")
    void cursorScoreOverflow() {
        assertThatThrownBy(() -> archiveQueryService.getArchives(USER_ID,
                search(null, ArchiveSort.POPULAR, "3000000000_1", null)))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_CURSOR);
        verifyNoInteractions(archiveRepository);
    }

    @Test
    @DisplayName("결과가 없으면 빈 응답이고 질문, 하트, 신고 조회를 하지 않는다")
    void emptySkipsLookups() {
        given(archiveRepository.findLatest(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());

        ArchiveListResponse result = archiveQueryService.getArchives(USER_ID, search(null, null, null, null));

        assertThat(result.archives()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        verify(questionRepository, never()).findByArchiveIds(any());
        verifyNoInteractions(likeRepository, reportRepository);
    }

    @Test
    @DisplayName("회사별 질문 수 커서 형식이 틀리면 400")
    void companiesInvalidCursor() {
        assertThatThrownBy(() -> archiveQueryService.getCompanies(null, "abc", null))
                .extracting("errorCode").isEqualTo(ArchiveErrorCode.INVALID_CURSOR);
        verifyNoInteractions(questionRepository);
    }

    private ArchiveSearch search(Long companyId, ArchiveSort sort, String cursor, Integer size) {
        return new ArchiveSearch(companyId, null, null, null, null, sort, cursor, size);
    }
}
