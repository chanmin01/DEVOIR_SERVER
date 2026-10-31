package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCard;
import com.benly.archive.dto.ArchiveListResponse;
import com.benly.archive.dto.ArchiveSearch;
import com.benly.archive.dto.CompanyListResponse;
import com.benly.archive.dto.CompanySearchResponse;
import com.benly.archive.entity.*;
import com.benly.archive.repository.*;
import com.benly.archive.support.ArchiveSort;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ArchiveQueryIntegrationTest {

    @Autowired private ArchiveQueryService archiveQueryService;
    @Autowired private CompanySearchService companySearchService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewArchiveRepository archiveRepository;
    @Autowired private ArchiveQuestionRepository questionRepository;
    @Autowired private ArchiveLikeRepository likeRepository;
    @Autowired private ArchiveReportRepository reportRepository;
    @Autowired private UserRepository userRepository;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.of("query-test-me", "나"));
        other = userRepository.save(User.of("query-test-other", "다른사람"));
    }

    @Test
    @DisplayName("최신순 카드: 숨김과 삭제는 빠지고, 질문은 등록 순서로 묶이고, 내 하트와 신고가 표시된다")
    void latestCardsAssembled() {
        Company company = company("조회테스트에이");
        InterviewArchive first = archive(other, company, CompanyType.SERVICE, "백엔드",
                "첫 번째로 받은 질문입니다", "두 번째로 받은 질문입니다");
        InterviewArchive mine = archive(me, company, CompanyType.SERVICE, "백엔드", "내가 올린 질문입니다");
        InterviewArchive hidden = archive(other, company, CompanyType.SERVICE, "백엔드", "숨겨진 질문입니다");
        ReflectionTestUtils.setField(hidden, "status", ArchiveStatus.HIDDEN);
        InterviewArchive deleted = archive(other, company, CompanyType.SERVICE, "백엔드", "삭제된 질문입니다");
        deleted.softDelete();

        ArchiveQuestion secondQuestion = questionRepository.findByArchiveIdOrderBySeqAsc(first.getId()).get(1);
        likeRepository.save(ArchiveLike.of(me, secondQuestion));
        reportRepository.save(ArchiveReport.of(me, first, ReportReason.AD));

        ArchiveListResponse result = archiveQueryService.getArchives(me.getId(), search(company.getId(), null, null, null, null));

        assertThat(result.archives()).extracting(ArchiveCard::archiveId)
                .containsExactly(mine.getId(), first.getId());
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();

        ArchiveCard myCard = result.archives().get(0);
        assertThat(myCard.mine()).isTrue();
        assertThat(myCard.reported()).isFalse();

        ArchiveCard otherCard = result.archives().get(1);
        assertThat(otherCard.mine()).isFalse();
        assertThat(otherCard.reported()).isTrue();
        assertThat(otherCard.memo()).isEqualTo("메모");
        assertThat(otherCard.interviewedOn()).isEqualTo("2026-06");
        assertThat(otherCard.questions()).extracting(ArchiveCard.Question::content)
                .containsExactly("첫 번째로 받은 질문입니다", "두 번째로 받은 질문입니다");
        assertThat(otherCard.questions()).extracting(ArchiveCard.Question::liked)
                .containsExactly(false, true);
    }

    @Test
    @DisplayName("인기순: 하트 합계순, 같으면 최신이 먼저, 커서로 다음 페이지가 이어진다")
    void popularCursorPaging() {
        Company company = company("조회테스트비");
        InterviewArchive low = archive(other, company, CompanyType.SERVICE, null, "하트 다섯 개 오래된 질문");
        InterviewArchive top = archive(other, company, CompanyType.SERVICE, null, "하트 아홉 개 질문입니다");
        InterviewArchive sameNewer = archive(other, company, CompanyType.SERVICE, null, "하트 다섯 개 최신 질문");
        ReflectionTestUtils.setField(low, "likeCount", 5);
        ReflectionTestUtils.setField(top, "likeCount", 9);
        ReflectionTestUtils.setField(sameNewer, "likeCount", 5);

        ArchiveListResponse page1 = archiveQueryService.getArchives(me.getId(),
                new ArchiveSearch(company.getId(), null, null, null, null, ArchiveSort.POPULAR, null, 2));

        assertThat(page1.archives()).extracting(ArchiveCard::archiveId)
                .containsExactly(top.getId(), sameNewer.getId());
        assertThat(page1.archives().get(0).likeCount()).isEqualTo(9);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.nextCursor()).isEqualTo("5_" + sameNewer.getId());

        ArchiveListResponse page2 = archiveQueryService.getArchives(me.getId(),
                new ArchiveSearch(company.getId(), null, null, null, null, ArchiveSort.POPULAR, page1.nextCursor(), 2));

        assertThat(page2.archives()).extracting(ArchiveCard::archiveId).containsExactly(low.getId());
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    @DisplayName("직무 필터가 걸리고, 빈 직무는 필터 없음으로 처리된다")
    void jobRoleFilter() {
        Company company = company("조회테스트씨");
        InterviewArchive backend = archive(other, company, CompanyType.SERVICE, "백엔드", "백엔드 면접 질문입니다");
        InterviewArchive frontend = archive(other, company, CompanyType.SERVICE, "프론트엔드", "프론트 면접 질문입니다");

        ArchiveListResponse filtered = archiveQueryService.getArchives(me.getId(),
                search(company.getId(), null, null, null, "프론트엔드"));
        assertThat(filtered.archives()).extracting(ArchiveCard::archiveId).containsExactly(frontend.getId());

        ArchiveListResponse blank = archiveQueryService.getArchives(me.getId(),
                search(company.getId(), null, null, null, "   "));
        assertThat(blank.archives()).extracting(ArchiveCard::archiveId)
                .containsExactly(frontend.getId(), backend.getId());
    }

    @Test
    @DisplayName("회사별 질문 수: 많은 순으로 세고, 숨긴 등록 건 질문은 빼고, 커서로 이어진다")
    void companyCounts() {
        Company many = company("집계테스트다수");
        Company few = company("집계테스트소수");
        archive(other, many, CompanyType.FINANCE_IT, null, "다수 회사 질문 하나", "다수 회사 질문 둘", "다수 회사 질문 셋");
        archive(other, few, CompanyType.FINANCE_IT, null, "소수 회사 보이는 질문");
        InterviewArchive hidden = archive(other, few, CompanyType.FINANCE_IT, null,
                "소수 회사 숨김 하나", "소수 회사 숨김 둘", "소수 회사 숨김 셋", "소수 회사 숨김 넷");
        ReflectionTestUtils.setField(hidden, "status", ArchiveStatus.HIDDEN);

        CompanyListResponse all = archiveQueryService.getCompanies(CompanyType.FINANCE_IT, null, 50);
        List<CompanyListResponse.Item> mineOnly = all.companies().stream()
                .filter(i -> i.name().startsWith("집계테스트")).toList();
        assertThat(mineOnly).extracting(CompanyListResponse.Item::name)
                .containsExactly("집계테스트다수", "집계테스트소수");
        assertThat(mineOnly).extracting(CompanyListResponse.Item::questionCount)
                .containsExactly(3L, 1L);
        assertThat(mineOnly.get(0).latestInterviewedOn()).isEqualTo("2026-06");

        CompanyListResponse page1 = archiveQueryService.getCompanies(CompanyType.FINANCE_IT, null, 1);
        assertThat(page1.hasNext()).isTrue();
        CompanyListResponse.Item firstItem = page1.companies().get(0);
        assertThat(page1.nextCursor()).isEqualTo(firstItem.questionCount() + "_" + firstItem.companyId());
    }

    @Test
    @DisplayName("회사 자동완성 질문 수에서 숨긴 등록 건의 질문은 빠진다")
    void autocompleteExcludesHidden() {
        Company company = company("숨김카운트테스트");
        archive(other, company, CompanyType.SERVICE, null, "보이는 자동완성 질문");
        InterviewArchive hidden = archive(other, company, CompanyType.SERVICE, null,
                "숨긴 자동완성 질문 하나", "숨긴 자동완성 질문 둘");
        ReflectionTestUtils.setField(hidden, "status", ArchiveStatus.HIDDEN);

        CompanySearchResponse result = companySearchService.search("숨김카운트", 20);

        assertThat(result.companies()).hasSize(1);
        assertThat(result.companies().get(0).questionCount()).isEqualTo(1);
    }

    private Company company(String name) {
        return companyRepository.save(Company.ofDart(null, name, name));
    }

    private InterviewArchive archive(User owner, Company company, CompanyType type, String jobRole, String... questions) {
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(
                owner, company, type, InterviewStage.TECHNICAL, InterviewFormat.QA,
                jobRole, YearMonth.of(2026, 6), "메모"));
        for (int i = 0; i < questions.length; i++) {
            questionRepository.save(ArchiveQuestion.of(archive, i + 1, questions[i]));
        }
        return archive;
    }

    private ArchiveSearch search(Long companyId, CompanyType type, InterviewStage stage, InterviewFormat format, String jobRole) {
        return new ArchiveSearch(companyId, type, stage, format, jobRole, null, null, null);
    }
}
