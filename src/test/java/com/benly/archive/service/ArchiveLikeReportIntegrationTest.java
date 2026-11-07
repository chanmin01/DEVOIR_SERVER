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

    @Test
    @DisplayName("질문 두 개에 하트하면 면접 기록 합계가 2, 하나 취소하면 1")
    void likeCountSync() {
        User viewer = user();
        InterviewArchive archive = archive(user());
        var questions = questionRepository.findByArchiveIdOrderBySeqAsc(archive.getId());
        Long q1 = questions.get(0).getId();
        Long q2 = questions.get(1).getId();

        likeService.like(viewer.getId(), q1);
        likeService.like(viewer.getId(), q2);
        var again = likeService.like(viewer.getId(), q2);   // 연타

        assertThat(again.archiveLikeCount()).isEqualTo(2);
        assertThat(archiveRepository.findLikeCount(archive.getId())).isEqualTo(2);
        assertThat(questionRepository.findLikeCount(q2)).isEqualTo(1);

        likeService.unlike(viewer.getId(), q2);
        var undo = likeService.unlike(viewer.getId(), q2);   // 연타

        assertThat(undo.likeCount()).isZero();
        assertThat(undo.archiveLikeCount()).isEqualTo(1);
        assertThat(archiveRepository.findLikeCount(archive.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("세 명이 신고하면 숨김으로 바뀌고, 숨김 뒤에도 신고는 쌓인다")
    void hideAfterThreeReports() {
        InterviewArchive archive = archive(user());

        reportService.report(user().getId(), archive.getId(), ReportReason.AD);
        reportService.report(user().getId(), archive.getId(), ReportReason.COPIED);
        assertThat(archive.getStatus()).isEqualTo(ArchiveStatus.VISIBLE);

        reportService.report(user().getId(), archive.getId(), ReportReason.FALSE_INFO);
        assertThat(archive.getStatus()).isEqualTo(ArchiveStatus.HIDDEN);

        reportService.report(user().getId(), archive.getId(), ReportReason.NDA_VIOLATION);
        assertThat(archive.getReportCount()).isEqualTo(4);
        assertThat(archive.getStatus()).isEqualTo(ArchiveStatus.HIDDEN);
    }
}
