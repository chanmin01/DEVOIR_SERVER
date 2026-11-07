package com.benly.archive.service;

import com.benly.archive.dto.ArchiveCreateRequest;
import com.benly.archive.entity.ArchiveQuestion;
import com.benly.archive.entity.Company;
import com.benly.archive.entity.InterviewArchive;
import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.exception.ArchiveErrorCode;
import com.benly.archive.repository.ArchiveQuestionRepository;
import com.benly.archive.repository.CompanyRepository;
import com.benly.archive.repository.InterviewArchiveRepository;
import com.benly.archive.support.ArchiveTime;
import com.benly.global.exception.BusinessException;
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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ArchiveRegisterConcurrencyTest {

    @Autowired
    private ArchiveRegisterService archiveRegisterService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private InterviewArchiveRepository archiveRepository;
    @Autowired
    private ArchiveQuestionRepository questionRepository;

    @Test
    @DisplayName("오늘 19개인 상태에서 다른 질문 2건이 동시에 와도 하나만 저장되고 20개를 넘지 않는다")
    void dailyLimitWithConcurrentRequests() throws Exception {
        User user = userRepository.save(User.of("concurrency-limit-test", "테스터"));
        Company company = companyRepository.save(Company.ofDart(null, "동시성한도", "동시성한도"));
        seedTodayQuestions(user, company, 19);

        List<Throwable> failures = runConcurrently(
                () -> archiveRegisterService.register(user.getId(),
                        request(company.getId(), "동시 요청 첫 번째 질문입니다.")),
                () -> archiveRegisterService.register(user.getId(),
                        request(company.getId(), "동시 요청 두 번째 질문입니다.")));

        assertThat(failures).hasSize(1);
        assertErrorCode(failures.get(0), ArchiveErrorCode.DAILY_LIMIT_EXCEEDED);
        assertThat(questionRepository.countQuestionsSince(user.getId(), ArchiveTime.startOfTodayKst()))
                .isEqualTo(20);
    }

    @Test
    @DisplayName("같은 내용이 동시에 두 번 와도 하나만 저장된다")
    void duplicateWithConcurrentRequests() throws Exception {
        User user = userRepository.save(User.of("concurrency-dup-test", "테스터"));
        Company company = companyRepository.save(Company.ofDart(null, "동시성중복", "동시성중복"));
        String question = "버튼을 두 번 누른 같은 질문입니다.";

        List<Throwable> failures = runConcurrently(
                () -> archiveRegisterService.register(user.getId(), request(company.getId(), question)),
                () -> archiveRegisterService.register(user.getId(), request(company.getId(), question)));

        assertThat(failures).hasSize(1);
        assertErrorCode(failures.get(0), ArchiveErrorCode.DUPLICATE_SUBMISSION);
        assertThat(questionRepository.countQuestionsSince(user.getId(), ArchiveTime.startOfTodayKst()))
                .isEqualTo(1);
    }

    private ArchiveCreateRequest request(Long companyId, String question) {
        return new ArchiveCreateRequest(companyId, null, "SERVICE", "TECHNICAL", "QA",
                null, "2026-03", null, List.of(question), true);
    }

    private void seedTodayQuestions(User user, Company company, int count) {
        int seq = 0;
        InterviewArchive archive = null;
        for (int i = 0; i < count; i++) {
            if (i % 10 == 0) {
                archive = archiveRepository.save(InterviewArchive.create(
                        user, company, CompanyType.SERVICE, InterviewStage.TECHNICAL, InterviewFormat.QA,
                        null, YearMonth.of(2026, 3), null));
                seq = 0;
            }
            questionRepository.save(ArchiveQuestion.of(archive, ++seq, "미리 채운 질문 " + i + "번입니다."));
        }
    }

    private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (Runnable task : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                awaitSilently(start);
                task.run();
            }));
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        return failures;
    }

    private void assertErrorCode(Throwable error, ArchiveErrorCode expected) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getErrorCode()).isEqualTo(expected);
    }

    private void awaitSilently(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
