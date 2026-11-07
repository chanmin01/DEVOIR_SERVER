package com.benly.archive.repository;

import com.benly.archive.dto.CompanySearchResponse;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.CompanyAlias;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.service.CompanySearchService;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CompanySearchIntegrationTest {

    @Autowired
    private CompanySearchService companySearchService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyAliasRepository companyAliasRepository;
    @Autowired
    private InterviewArchiveRepository archiveRepository;
    @Autowired
    private ArchiveQuestionRepository questionRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("한 회사가 별칭 50개 넘게 걸려도 별칭으로만 일치하는 다른 회사가 빠지지 않는다")
    void aliasLimitAppliesPerCompany() {
        Company big = companyRepository.save(Company.ofDart(null, "베타본사", "베타본사"));
        for (int i = 1; i <= 55; i++) {
            companyAliasRepository.save(CompanyAlias.of(big, "알파" + i, "알파" + i));
        }
        User user = userRepository.save(User.of("alias-limit-test", "테스터"));
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(
                user, big, CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA,
                null, YearMonth.of(2026, 3), null));
        questionRepository.save(ArchiveQuestion.of(archive, 1, "별칭 상한 테스트용 질문입니다."));

        Company small = companyRepository.save(Company.ofDart(null, "감마상사", "감마상사"));
        companyAliasRepository.save(CompanyAlias.of(small, "알파감마", "알파감마"));

        CompanySearchResponse result = companySearchService.search("알파", 20);

        assertThat(result.companies())
                .extracting(CompanySearchResponse.Item::name)
                .containsExactly("베타본사", "감마상사");
        assertThat(result.companies().get(0).questionCount()).isEqualTo(1);
    }
}
