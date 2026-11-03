package com.benly.archive.service;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.ArchiveStatus;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.entity.ReportReason;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.InterviewStage;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ArchiveLikeReportIntegrationTest {

    @Autowired private ArchiveLikeService likeService;
    @Autowired private ArchiveReportService reportService;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewArchiveRepository archiveRepository;
    @Autowired private ArchiveQuestionRepository questionRepository;

    private User user() {
        return userRepository.save(User.of("archive-test-" + System.nanoTime(), "테스터"));
    }

    private InterviewArchive archive(User owner) {
        String key = "테스트회사" + System.nanoTime();
        Company company = companyRepository.save(Company.ofUser(key, key));
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(owner, company,
                CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA, null, YearMonth.of(2026, 10), null));
        questionRepository.save(ArchiveQuestion.of(archive, 1, "트랜잭션 격리 수준 설명해 주세요"));
        questionRepository.save(ArchiveQuestion.of(archive, 2, "인덱스는 언제 안 타나요"));
        return archive;
    }

    private InterviewArchive reload(InterviewArchive a) {
        return archiveRepository.findById(a.getId()).orElseThrow();
    }

    @Test
    @DisplayName("질문 두 개에 하트하면 면접 기록 합계가 2, 하나 취소하면 1")
    void likeCountSync() {
        User owner = user();
        User viewer = user();
        InterviewArchive archive = archive(owner);
        var questions = questionRepository.findByArchiveIdOrderBySeqAsc(archive.getId());

        likeService.like(viewer.getId(), questions.get(0).getId());
        likeService.like(viewer.getId(), questions.get(1).getId());
        likeService.like(viewer.getId(), questions.get(1).getId());   // 연타

        assertThat(reload(archive).getLikeCount()).isEqualTo(2);
        assertThat(questionRepository.findLikeCount(questions.get(1).getId())).isEqualTo(1);

        likeService.unlike(viewer.getId(), questions.get(1).getId());
        likeService.unlike(viewer.getId(), questions.get(1).getId());   // 연타

        assertThat(reload(archive).getLikeCount()).isEqualTo(1);
        assertThat(questionRepository.findLikeCount(questions.get(1).getId())).isZero();
    }

    @Test
    @DisplayName("서로 다른 세 명이 신고하면 숨김으로 바뀐다")
    void hideAfterThreeReports() {
        InterviewArchive archive = archive(user());

        reportService.report(user().getId(), archive.getId(), ReportReason.AD);
        reportService.report(user().getId(), archive.getId(), ReportReason.COPIED);
        assertThat(reload(archive).getStatus()).isEqualTo(ArchiveStatus.VISIBLE);

        reportService.report(user().getId(), archive.getId(), ReportReason.FALSE_INFO);

        InterviewArchive reloaded = reload(archive);
        assertThat(reloaded.getReportCount()).isEqualTo(3);
        assertThat(reloaded.getStatus()).isEqualTo(ArchiveStatus.HIDDEN);
    }
}
