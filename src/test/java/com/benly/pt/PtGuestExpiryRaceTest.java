package com.benly.pt;

import com.benly.archive.entity.Company;
import com.benly.archive.repository.CompanyRepository;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.repository.*;
import com.benly.pt.service.*;
import com.benly.question.repository.AnswerRepository;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.entity.CompanyType;
import com.benly.session.entity.SessionStatus;
import com.benly.session.repository.SessionRepository;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PtFinalTestConfig.class)
class PtGuestExpiryRaceTest {
    private static final Instant START = Instant.parse("2026-11-06T00:00:00Z");

    @Autowired PtSessionService create;
    @Autowired PtProgressService progress;
    @Autowired PtInterviewService interview;
    @Autowired PtInterviewTx answersTx;
    @Autowired PtGuestExpiryTx expiry;
    @Autowired PtQuestionGenerator generator;
    @Autowired PtSessionRepository details;
    @Autowired PtTaskRepository tasks;
    @Autowired PtAudioReceiptRepository receipts;
    @Autowired PtQuestionTurnRepository turns;
    @Autowired AnswerRepository answers;
    @Autowired QuestionRepository questions;
    @Autowired SessionRepository sessions;
    @Autowired CompanyRepository companies;
    @Autowired UserRepository users;
    @Autowired PtContentCodec codec;
    @Autowired JpaTransactionManager manager;
    @Autowired PtFinalTestConfig.TestClock clock;
    @Autowired PtFinalTestConfig.PtSqlProbe probe;

    Long ownerId;
    Long companyId;

    @BeforeEach
    void setup() {
        clock.now = START;
        probe.observer = sql -> {};
        reset(generator);
        doAnswer(call -> "질문 " + ((PtGenerationPlan) call.getArgument(0)).ordinal())
                .when(generator).generate(any());
        inTx(() -> {
            receipts.deleteAll();
            turns.deleteAll();
            answers.deleteAll();
            questions.deleteAll();
            details.deleteAll();
            sessions.deleteAll();
            tasks.deleteAll();
            companies.deleteAll();
            users.deleteAll();
            ownerId = users.saveAndFlush(User.createGuest()).getId();
            var company = companies.saveAndFlush(Company.ofUser("기업은행", "기업은행"));
            companyId = company.getId();
            var content = new PtTaskContent("가상 과제", "서비스 설명", List.of("조회"),
                    "문제 상황", List.of("자료"), List.of("제약"), List.of("연습용 가정"),
                    Map.of(PtTrack.AI, "AI 개선"), List.of("개선 방안 발표"));
            tasks.saveAndFlush(PtTask.approvedSample(company, CompanyType.FINANCE_IT,
                    "EXPIRY_RACE", codec.content(content)));
        });
    }

    private void inTx(Runnable action) {
        new TransactionTemplate(manager).execute(status -> {
            action.run();
            return null;
        });
    }

    @Test
    void expiryPreservesLastAnswerCommittedAfterEarlierSnapshot() throws Exception {
        Long id = create.create(ownerId,
                        new PtSessionCreateRequest(UUID.randomUUID(), companyId, 20, "백엔드"))
                .sessionId();
        progress.selectTrack(ownerId, id, new PtTrackRequest(PtTrack.AI));
        progress.startPresentation(ownerId, id);
        progress.submitText(ownerId, id,
                new PtTextPresentationRequest(UUID.randomUUID(), "AI 개선 방안을 발표합니다."));
        for (int n = 0; n < 5; n++) {
            var question = interview.next(ownerId, id).question();
            answersTx.submitText(ownerId, id, question.questionId(),
                    new PtAnswerRequest(UUID.randomUUID(), "질문에 대한 답변입니다."));
        }
        var last = interview.next(ownerId, id).question();
        var lockedSelect = new AtomicReference<String>();

        try (var pool = Executors.newSingleThreadExecutor()) {
            inTx(() -> {
                // MySQL REPEATABLE READ에서 마지막 답변 이전의 일관 읽기를 만든다.
                details.findOwnerId(id);
                try {
                    pool.submit(() -> answersTx.submitText(ownerId, id, last.questionId(),
                                    new PtAnswerRequest(UUID.randomUUID(), "마지막 답변입니다.")))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }

                clock.now = START.plusSeconds(86400);
                probe.observer = sql -> {
                    String normalized = sql.toLowerCase(Locale.ROOT);
                    if (normalized.startsWith("select")
                            && normalized.contains("from pt_sessions")
                            && normalized.contains("for update")) {
                        lockedSelect.set(normalized);
                    }
                };
                assertTrue(expiry.expire(id));
            });
        } finally {
            probe.observer = sql -> {};
        }

        var saved = details.findById(id).orElseThrow();
        assertEquals(PtPhase.COMPLETED, saved.getPhase());
        assertEquals(SessionStatus.COMPLETED, sessions.findById(id).orElseThrow().getStatus());
        assertNotNull(saved.getRetentionClosedAt());
        assertEquals(6, answers.count());
        assertNotNull(lockedSelect.get(), "PT 상세를 잠금 읽기로 조회해야 합니다.");
    }
}