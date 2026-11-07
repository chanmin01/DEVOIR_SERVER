package com.benly.archive.service;

import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.repository.ArchiveLikeRepository;
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

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArchiveLikeConcurrencyTest {

    @Autowired private ArchiveLikeService likeService;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private InterviewArchiveRepository archiveRepository;
    @Autowired private ArchiveQuestionRepository questionRepository;
    @Autowired private ArchiveLikeRepository likeRepository;

    private User user() {
        return userRepository.save(User.of("like-conc-" + System.nanoTime(), "테스터"));
    }

    private ArchiveQuestion question() {
        String key = "동시성회사" + System.nanoTime();
        Company company = companyRepository.save(Company.ofUser(key, key));
        InterviewArchive archive = archiveRepository.save(InterviewArchive.create(user(), company,
                CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA, null, YearMonth.of(2026, 10), null));
        return questionRepository.save(ArchiveQuestion.of(archive, 1, "동시성 테스트 질문입니다"));
    }

    private long likeRows(Long questionId) {
        return likeRepository.findAll().stream()
                .filter(l -> l.getQuestion().getId().equals(questionId))
                .count();
    }

    private void runTogether(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        for (Runnable task : tasks) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("여러 명이 동시에 하트를 눌러도 두 하트 수가 하트 행 수와 같다")
    void manyUsersLikeTogether() throws InterruptedException {
        ArchiveQuestion question = question();
        Long questionId = question.getId();
        Long archiveId = question.getArchive().getId();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Long userId = user().getId();
            tasks.add(() -> likeService.like(userId, questionId));
        }

        runTogether(tasks);

        assertThat(questionRepository.findLikeCount(questionId)).isEqualTo(10);
        assertThat(archiveRepository.findLikeCount(archiveId)).isEqualTo(10);
        assertThat(likeRows(questionId)).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 사람이 하트와 취소를 동시에 눌러도 숫자가 하트 행 수와 어긋나지 않는다")
    void likeAndUnlikeTogether() throws InterruptedException {
        ArchiveQuestion question = question();
        Long questionId = question.getId();
        Long archiveId = question.getArchive().getId();
        Long userId = user().getId();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> likeService.like(userId, questionId));
            tasks.add(() -> likeService.unlike(userId, questionId));
        }

        runTogether(tasks);

        long rows = likeRows(questionId);
        assertThat(rows).isBetween(0L, 1L);
        assertThat(questionRepository.findLikeCount(questionId)).isEqualTo((int) rows);
        assertThat(archiveRepository.findLikeCount(archiveId)).isEqualTo((int) rows);
    }
}
