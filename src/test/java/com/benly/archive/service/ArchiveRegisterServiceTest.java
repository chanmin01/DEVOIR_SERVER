package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.entity.Company;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.support.ArchiveTime;
import com.benly.global.exception.BusinessException;
import com.benly.session.entity.CompanyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArchiveRegisterServiceTest {

    private static final Long USER_ID = 1L;
    private static final String QUESTION = "Redis를 캐시로 쓰다가 장애가 나면 어떻게 되나요?";

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanyResolver companyResolver;
    @Mock
    private ArchiveWriter archiveWriter;

    @InjectMocks
    private ArchiveRegisterService archiveRegisterService;

    private ArchiveCreateRequest request(Long companyId, String companyName, String stage, String format,
                                         String month, String jobRole, List<String> questions) {
        return new ArchiveCreateRequest(companyId, companyName, "SERVICE", stage, format,
                jobRole, month, null, questions, true);
    }

    private ArchiveCreateRequest validRequest() {
        return request(3L, null, "TECHNICAL", "QA", "2026-03", "백엔드", List.of(QUESTION));
    }

    private Company kakao() {
        Company company = Company.ofDart(null, "카카오", "카카오");
        ReflectionTestUtils.setField(company, "id", 3L);
        return company;
    }

    private void assertError(ArchiveCreateRequest req, ArchiveErrorCode expected) {
        assertThatThrownBy(() -> archiveRegisterService.register(USER_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    @DisplayName("PT 면접인데 인성 단계면 400")
    void ptWithPersonality() {
        assertError(request(3L, null, "PERSONALITY", "PT", "2026-03", null, List.of(QUESTION)),
                ArchiveErrorCode.PT_ONLY_TECHNICAL);
    }

    @Test
    @DisplayName("한국 시간 기준 다음 달이면 400")
    void futureMonth() {
        String nextMonth = ArchiveTime.currentMonthKst().plusMonths(1).toString();
        assertError(request(3L, null, "TECHNICAL", "QA", nextMonth, null, List.of(QUESTION)),
                ArchiveErrorCode.FUTURE_INTERVIEW_MONTH);
    }

    @Test
    @DisplayName("면접 시기가 2000년 1월 이전이면 400")
    void tooOldMonth() {
        assertError(request(3L, null, "TECHNICAL", "QA", "1999-12", null, List.of(QUESTION)),
                ArchiveErrorCode.INVALID_INTERVIEW_MONTH);
    }

    @Test
    @DisplayName("공백을 자른 뒤 5자 미만인 질문이 있으면 400")
    void questionTooShortAfterTrim() {
        assertError(request(3L, null, "TECHNICAL", "QA", "2026-03", null, List.of("   abc   ")),
                ArchiveErrorCode.QUESTION_TOO_SHORT);
    }

    @Test
    @DisplayName("공백만 다른 같은 질문이 두 번 있으면 400")
    void duplicateQuestionInRequest() {
        assertError(request(3L, null, "TECHNICAL", "QA", "2026-03", null, List.of(QUESTION, "  " + QUESTION)),
                ArchiveErrorCode.DUPLICATE_QUESTION_IN_REQUEST);
    }

    @Test
    @DisplayName("회사 id와 이름을 둘 다 보내면 400")
    void companyInputConflict() {
        assertError(request(3L, "카카오", "TECHNICAL", "QA", "2026-03", null, List.of(QUESTION)),
                ArchiveErrorCode.COMPANY_INPUT_CONFLICT);
    }

    @Test
    @DisplayName("회사 id도 이름도 없으면 400")
    void companyInputRequired() {
        assertError(request(null, "  ", "TECHNICAL", "QA", "2026-03", null, List.of(QUESTION)),
                ArchiveErrorCode.COMPANY_INPUT_REQUIRED);
    }

    @Test
    @DisplayName("없는 회사 id면 404")
    void companyNotFound() {
        given(companyRepository.findById(3L)).willReturn(Optional.empty());

        assertError(validRequest(), ArchiveErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 요청이면 질문 공백을 자르고 빈 직무는 null로 바꿔 저장을 맡긴다")
    void success() {
        given(companyRepository.findById(3L)).willReturn(Optional.of(kakao()));

        archiveRegisterService.register(USER_ID,
                request(3L, null, "TECHNICAL", "QA", "2026-03", "  ", List.of("  " + QUESTION + "  ")));

        ArgumentCaptor<ArchiveWriter.NewArchive> captor = ArgumentCaptor.forClass(ArchiveWriter.NewArchive.class);
        verify(archiveWriter).save(eq(USER_ID), any(ResolvedCompany.class), captor.capture());

        ArchiveWriter.NewArchive draft = captor.getValue();
        assertThat(draft.questions()).containsExactly(QUESTION);
        assertThat(draft.jobRole()).isNull();
        assertThat(draft.interviewedMonth()).isEqualTo(YearMonth.of(2026, 3));
        assertThat(draft.companyType()).isEqualTo(CompanyType.SERVICE);
    }
}
