package com.benly.pt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.benly.archive.entity.Company;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.*;
import com.benly.pt.service.*;
import com.benly.question.client.WhisperClient;
import com.benly.question.entity.AnswerType;
import com.benly.question.repository.*;
import com.benly.session.entity.*;
import com.benly.session.repository.SessionRepository;
import com.benly.session.service.SessionService;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringJUnitConfig(PtFinalTestConfig.class)
class PtCombinedIntegrationTest {
    static final Instant START = Instant.parse("2026-11-05T00:00:00Z");
    @Autowired PtSessionService create;
    @Autowired PtProgressService progress;
    @Autowired PtInterviewService interview;
    @Autowired PtInterviewTx tx;
    @Autowired PtAudioService audio;
    @Autowired PtQuestionGenerator generator;
    @Autowired WhisperClient whisper;
    @Autowired PtQuestionTurnRepository turns;
    @Autowired PtAudioReceiptRepository receipts;
    @Autowired PtSessionRepository details;
    @Autowired PtTaskRepository tasks;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRepository answers;
    @Autowired SessionRepository parents;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired PtContentCodec codec;
    @Autowired PtGuestExpiryTx expiry;
    @Autowired PtGuestExpiryJob expiryJob;
    @Autowired PtFinalTestConfig.TestClock clock;
    @Autowired JpaTransactionManager manager;
    @Autowired SessionService legacy;
    @Autowired PtFinalTestConfig.PtSqlProbe probe;
    Long companyId, userId, otherId, guestId;

    @BeforeEach
    void setup() {
        probe.observer = sql -> {};
        clock.now = START;
        reset(generator, whisper);
        doAnswer(
                i -> {
                    assertFalse(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return "PT 질문 " + ((PtGenerationPlan) i.getArgument(0)).ordinal();
                })
                .when(generator)
                .generate(any());
        doAnswer(
                i -> {
                    assertFalse(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return "음성에서 변환된 발표 또는 답변입니다.";
                })
                .when(whisper)
                .transcribe(any());
        inTx(
                () -> {
                    receipts.deleteAll();
                    turns.deleteAll();
                    answers.deleteAll();
                    questions.deleteAll();
                    details.deleteAll();
                    parents.deleteAll();
                    tasks.deleteAll();
                    companies.deleteAll();
                    users.deleteAll();
                    var company = companies.saveAndFlush(Company.ofUser("기업은행", "기업은행"));
                    companyId = company.getId();
                    userId = users.saveAndFlush(User.of("combined-test", "테스트회원")).getId();
                    otherId = users.saveAndFlush(User.of("combined-other", "테스트회원2")).getId();
                    guestId = users.saveAndFlush(User.createGuest()).getId();
                    var content =
                            new PtTaskContent(
                                    "가상 PT 과제",
                                    "가상 금융 서비스",
                                    List.of("조회", "추천"),
                                    "추천 이유가 불명확합니다.",
                                    List.of("재무 정보"),
                                    List.of("민감 정보 보호"),
                                    List.of("연습용 가정"),
                                    Map.of(PtTrack.AI, "추천 설명 개선"),
                                    List.of("5분 발표"));
                    tasks.saveAndFlush(
                            PtTask.approvedSample(
                                    company,
                                    CompanyType.FINANCE_IT,
                                    "FINAL",
                                    codec.content(content)));
                });
    }

    void inTx(Runnable action) {
        new TransactionTemplate(manager)
                .execute(
                        s -> {
                            action.run();
                            return null;
                        });
    }

    Long newSession(Long owner) {
        return create.create(
                        owner, new PtSessionCreateRequest(UUID.randomUUID(), companyId, 20, "백엔드"))
                .sessionId();
    }

    Long presenting(Long owner) {
        Long id = newSession(owner);
        progress.selectTrack(owner, id, new PtTrackRequest(PtTrack.AI));
        progress.startPresentation(owner, id);
        return id;
    }

    Long ready(Long owner) {
        Long id = presenting(owner);
        progress.submitText(
                owner,
                id,
                new PtTextPresentationRequest(
                        UUID.randomUUID(), "AI 추천 이유를 설명하는 기능을 단계적으로 도입하겠습니다."));
        return id;
    }

    PtAnswerRequest text(UUID requestId, String value) {
        return new PtAnswerRequest(requestId, value);
    }

    MockMultipartFile file(String content) {
        return new MockMultipartFile(
                "audio",
                "recording.webm",
                "audio/webm",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    void fails(PtErrorCode code, Runnable action) {
        var e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getErrorCode());
    }

    @Test
    void fullSixQuestionsCompleteAndExposeEvaluationInput() {
        Long id = ready(userId);
        for (int ordinal = 1; ordinal <= 6; ordinal++) {
            var state = interview.next(userId, id);
            var q = state.question();
            assertEquals(ordinal, q.ordinal());
            assertEquals((ordinal - 1) / 3 + 1, q.mainNumber());
            assertEquals((ordinal - 1) % 3, q.followUpNumber());
            assertEquals(q.followUpNumber() == 0 ? "MAIN" : "FOLLOW_UP", q.type());
            assertEquals(PtNextAction.ANSWER, state.nextAction());
            var answer =
                    tx.submitText(
                            userId,
                            id,
                            q.questionId(),
                            text(UUID.randomUUID(), "근거와 실행 방안을 설명하는 답변 " + ordinal));
            assertEquals(ordinal, answer.progress().answeredCount());
            assertEquals(
                    ordinal == 6 ? PtPhase.COMPLETED : PtPhase.GENERATING,
                    answer.progress().phase());
        }
        assertEquals(SessionStatus.COMPLETED, parents.findById(id).orElseThrow().getStatus());
        assertEquals(PtPhase.COMPLETED, details.findById(id).orElseThrow().getPhase());
        var input = tx.evaluationInput(userId, id);
        assertEquals(1, input.schemaVersion());
        assertEquals(6, input.turns().size());
        assertEquals(PtTrack.AI, input.track());
        assertTrue(input.presentation().transcript().contains("AI 추천"));
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6),
                input.turns().stream().map(t -> t.question().ordinal()).toList());
        inTx(
                () -> {
                    var all = turns.findByPtSession_IdOrderByOrdinalAsc(id);
                    assertEquals(
                            all.get(0).getQuestion().getId(),
                            all.get(1).getQuestion().getParent().getId());
                    assertEquals(
                            all.get(0).getQuestion().getId(),
                            all.get(2).getQuestion().getParent().getId());
                    assertEquals(
                            all.get(3).getQuestion().getId(),
                            all.get(4).getQuestion().getParent().getId());
                });
        var finished = interview.next(userId, id);
        assertEquals(PtNextAction.FINISH, finished.nextAction());
        assertNull(finished.question());
        verify(generator, times(6)).generate(any());
    }

    @Test
    void nextAndCurrentReplayExistingQuestionWithoutGenerating() {
        Long id = ready(userId);
        var first = interview.next(userId, id);
        assertEquals(first.question(), interview.next(userId, id).question());
        assertEquals(first.question(), tx.current(userId, id).question());
        verify(generator, times(1)).generate(any());
    }

    @Test
    void refusesQuestionBeforePresentation() {
        Long id = presenting(userId);
        fails(PtErrorCode.INVALID_PHASE, () -> interview.next(userId, id));
        verifyNoInteractions(generator);
    }

    @Test
    void generationFailurePreservesAnswersAndCanRetrySameSlot() {
        Long id = ready(userId);
        Long first = interview.next(userId, id).question().questionId();
        tx.submitText(userId, id, first, text(UUID.randomUUID(), "비용과 효과를 분석합니다."));
        doThrow(new IllegalStateException("test failure")).when(generator).generate(any());
        fails(PtErrorCode.GENERATION_FAILED, () -> interview.next(userId, id));
        assertEquals(1, answers.count());
        assertFalse(tx.current(userId, id).processing());
        assertEquals(PtPhase.GENERATING, tx.current(userId, id).phase());
        doReturn("복구된 꼬리질문").when(generator).generate(any());
        assertEquals(2, interview.next(userId, id).question().ordinal());
    }

    @Test
    void malformedAndRepeatedQuestionsAreNotSaved() {
        Long id = ready(userId);
        doReturn(" ").when(generator).generate(any());
        fails(PtErrorCode.GENERATION_FAILED, () -> interview.next(userId, id));
        assertEquals(0, questions.count());
        doReturn("같은 질문").when(generator).generate(any());
        var q = interview.next(userId, id).question();
        tx.submitText(userId, id, q.questionId(), text(UUID.randomUUID(), "발표 근거"));
        fails(PtErrorCode.GENERATION_FAILED, () -> interview.next(userId, id));
        assertEquals(1, questions.count());
    }

    @Test
    void promptContainsTaskTrackPresentationAndPreviousAnswer() {
        Long id = ready(userId);
        var first = interview.next(userId, id).question();
        tx.submitText(
                userId, id, first.questionId(), text(UUID.randomUUID(), "회귀 테스트로 위험을 검증합니다."));
        doAnswer(
                i -> {
                    PtGenerationPlan p = i.getArgument(0);
                    assertEquals(1, p.mainNumber());
                    assertEquals(1, p.followUpNumber());
                    assertTrue(p.context().contains("회귀 테스트"));
                    assertTrue(p.context().contains("AI 추천 이유"));
                    assertTrue(p.context().contains("가상 PT 과제"));
                    return "위험 검증의 기준은 무엇인가요?";
                })
                .when(generator)
                .generate(any());
        interview.next(userId, id);
    }

    @Test
    void answerReplayAndConflictAreControlled() {
        Long id = ready(userId);
        var q = interview.next(userId, id).question();
        UUID request = UUID.randomUUID();
        var first = tx.submitText(userId, id, q.questionId(), text(request, " 원문 답변 "));
        assertEquals(
                first.answerId(),
                tx.submitText(userId, id, q.questionId(), text(request, "원문 답변")).answerId());
        fails(
                PtErrorCode.ANSWER_CONFLICT,
                () -> tx.submitText(userId, id, q.questionId(), text(request, "수정 답변")));
        fails(
                PtErrorCode.ANSWER_CONFLICT,
                () -> tx.submitText(userId, id, q.questionId(), text(UUID.randomUUID(), "원문 답변")));
        assertEquals(1, answers.count());
    }

    @Test
    void answerRequestIdCannotBeReusedForAnotherQuestion() {
        Long id = ready(userId);
        UUID request = UUID.randomUUID();
        var first = interview.next(userId, id).question();
        tx.submitText(userId, id, first.questionId(), text(request, "답변"));
        var second = interview.next(userId, id).question();
        fails(
                PtErrorCode.REQUEST_CONFLICT,
                () -> tx.submitText(userId, id, second.questionId(), text(request, "답변")));
    }

    @Test
    void lastAnswerCanBeReplayedAfterCompletion() {
        Long id = ready(userId);
        Long last = null;
        UUID request = null;
        for (int n = 0; n < 6; n++) {
            last = interview.next(userId, id).question().questionId();
            request = UUID.randomUUID();
            tx.submitText(userId, id, last, text(request, "답변"));
        }
        var replay = tx.submitText(userId, id, last, text(request, "답변"));
        assertEquals(PtNextAction.FINISH, replay.progress().nextAction());
        assertEquals(6, answers.count());
    }

    @Test
    void validatesBodyOwnershipQuestionAndExpiry() {
        Long id = ready(userId);
        var q = interview.next(userId, id).question();
        fails(
                PtErrorCode.INVALID_REQUEST,
                () -> tx.submitText(userId, id, q.questionId(), text(UUID.randomUUID(), " ")));
        fails(
                PtErrorCode.INVALID_REQUEST,
                () ->
                        tx.submitText(
                                userId,
                                id,
                                q.questionId(),
                                text(UUID.randomUUID(), "가".repeat(30001))));
        fails(
                PtErrorCode.QUESTION_NOT_FOUND,
                () -> tx.submitText(userId, id, Long.MAX_VALUE, text(UUID.randomUUID(), "답변")));
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> tx.current(otherId, id));
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> interview.next(otherId, id));
        fails(PtErrorCode.USER_UNAVAILABLE, () -> tx.current(null, id));
        Long guest = ready(guestId);
        clock.now = START.plusSeconds(86400);
        fails(PtErrorCode.SESSION_EXPIRED, () -> interview.next(guestId, guest));
        fails(PtErrorCode.SESSION_EXPIRED, () -> tx.current(guestId, guest));
    }

    @Test
    void withdrawnUserAndCanceledSessionCannotProceed() {
        Long id = ready(userId);
        progress.cancel(userId, id);
        fails(PtErrorCode.SESSION_NOT_IN_PROGRESS, () -> interview.next(userId, id));
        assertEquals(PtPhase.CANCELED, tx.current(userId, id).phase());
        inTx(() -> users.findById(userId).orElseThrow().withdraw());
        fails(PtErrorCode.USER_UNAVAILABLE, () -> tx.current(userId, id));
    }

    @Test
    void evaluationIsAvailableOnlyAfterAllSixAnswers() {
        Long id = ready(userId);
        fails(PtErrorCode.INTERVIEW_NOT_COMPLETED, () -> tx.evaluationInput(userId, id));
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> tx.evaluationInput(otherId, id));
    }

    @Test
    void staleGenerationCannotOverwriteNewAttempt() {
        Long id = ready(userId);
        var old = tx.prepareNext(userId, id);
        clock.now = START.plusSeconds(91);
        var fresh = tx.prepareNext(userId, id);
        fails(PtErrorCode.OPERATION_EXPIRED, () -> tx.finishGeneration(userId, id, old, "오래된 질문"));
        tx.abandon(userId, id, old.operationToken());
        assertEquals("새 질문", tx.finishGeneration(userId, id, fresh, "새 질문").question().content());
        assertEquals(1, questions.count());
    }

    @Test
    void concurrentNextCallsDoNotGenerateTwoQuestions() throws Exception {
        Long id = ready(userId);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                i -> {
                    entered.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return "단일 질문";
                })
                .when(generator)
                .generate(any());
        try (var pool = Executors.newFixedThreadPool(1)) {
            var pending = pool.submit(() -> interview.next(userId, id));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                assertTrue(tx.current(userId, id).processing());
                fails(PtErrorCode.REQUEST_IN_PROGRESS, () -> interview.next(userId, id));
                var presentation = details.findById(id).orElseThrow();
                assertEquals(
                        PtPhase.GENERATING,
                        progress.submitText(
                                        userId,
                                        id,
                                        new PtTextPresentationRequest(
                                                UUID.fromString(
                                                        presentation.getPresentationRequestId()),
                                                presentation.getPresentationTranscript()))
                                .phase());
            } finally {
                release.countDown();
            }
            assertEquals(1, pending.get(10, TimeUnit.SECONDS).question().ordinal());
        }
        assertEquals(1, questions.count());
        verify(generator, times(1)).generate(any());
    }

    @Test
    void cancelDuringGenerationDiscardsExternalResult() throws Exception {
        Long id = ready(userId);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                i -> {
                    entered.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return "취소 이후 도착한 질문";
                })
                .when(generator)
                .generate(any());
        try (var pool = Executors.newFixedThreadPool(1)) {
            var pending =
                    pool.submit(
                            () -> {
                                try {
                                    interview.next(userId, id);
                                    return null;
                                } catch (BusinessException e) {
                                    return e.getErrorCode();
                                }
                            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                legacy.cancelSession(userId, id);
            } finally {
                release.countDown();
            }
            assertEquals(PtErrorCode.SESSION_NOT_IN_PROGRESS, pending.get(10, TimeUnit.SECONDS));
        }
        assertEquals(0, questions.count());
        assertEquals(PtPhase.CANCELED, details.findById(id).orElseThrow().getPhase());
    }

    @Test
    void audioPresentationIsStoredAsAudioAndReplaySkipsWhisper() {
        Long id = presenting(userId);
        UUID request = UUID.randomUUID();
        var first = audio.presentation(userId, id, request, file("audio1"), 120);
        assertEquals(AnswerType.AUDIO, first.presentation().inputType());
        clock.now = START.plusSeconds(3600);
        var replay = audio.presentation(userId, id, request, file("audio1"), 120);
        assertEquals(first.presentation(), replay.presentation());
        verify(whisper, times(1)).transcribe(any());
    }

    @Test
    void audioUploadedInGraceCanFinishTranscriptionAfterGrace() {
        Long id = presenting(userId);
        clock.now = START.plusSeconds(350);
        doAnswer(
                i -> {
                    clock.now = START.plusSeconds(450);
                    return "지연 전사된 발표";
                })
                .when(whisper)
                .transcribe(any());
        var result = audio.presentation(userId, id, UUID.randomUUID(), file("audio"), 300);
        assertEquals(PtPhase.GENERATING, result.phase());
        assertEquals(START.plusSeconds(450), result.presentation().submittedAt());
    }

    @Test
    void lateFirstUploadIsRejectedBeforeWhisper() {
        Long id = presenting(userId);
        clock.now = START.plusSeconds(361);
        fails(
                PtErrorCode.PRESENTATION_SUBMISSION_EXPIRED,
                () -> audio.presentation(userId, id, UUID.randomUUID(), file("audio"), 100));
        verifyNoInteractions(whisper);
        assertEquals(0, receipts.count());
    }

    @Test
    void failedAudioCanRetrySamePreAdmittedFileAfterDeadline() {
        Long id = presenting(userId);
        UUID request = UUID.randomUUID();
        clock.now = START.plusSeconds(350);
        doThrow(new IllegalStateException("fake STT failure")).when(whisper).transcribe(any());
        fails(
                PtErrorCode.TRANSCRIPTION_FAILED,
                () -> audio.presentation(userId, id, request, file("audio"), 100));
        assertFalse(tx.current(userId, id).processing());
        clock.now = START.plusSeconds(3600);
        fails(
                PtErrorCode.REQUEST_CONFLICT,
                () -> audio.presentation(userId, id, request, file("different"), 100));
        doReturn("재시도에 성공한 발표").when(whisper).transcribe(any());
        assertEquals(
                PtPhase.GENERATING,
                audio.presentation(userId, id, request, file("audio"), 100).phase());
        assertEquals(1, receipts.count());
    }

    @Test
    void audioAnswerAndEvaluationPreserveInputTypeAndDuration() {
        Long id = ready(userId);
        var q = interview.next(userId, id).question();
        UUID request = UUID.randomUUID();
        var answer = audio.answer(userId, id, q.questionId(), request, file("answer"), 35);
        assertEquals(AnswerType.AUDIO, answer.inputType());
        assertEquals(35, answer.durationSec());
        assertEquals(
                answer.answerId(),
                audio.answer(userId, id, q.questionId(), request, file("answer"), 35).answerId());
        fails(
                PtErrorCode.ANSWER_CONFLICT,
                () -> audio.answer(userId, id, q.questionId(), request, file("new"), 35));
        verify(whisper, times(1)).transcribe(any());
    }

    @Test
    void blankWhisperResponseIsNotSaved() {
        Long id = ready(userId);
        var q = interview.next(userId, id).question();
        doReturn(" ").when(whisper).transcribe(any());
        fails(
                PtErrorCode.TRANSCRIPTION_FAILED,
                () ->
                        audio.answer(
                                userId, id, q.questionId(), UUID.randomUUID(), file("audio"), 30));
        assertEquals(0, answers.count());
        assertFalse(tx.current(userId, id).processing());
    }

    @Test
    void audioAdmissionCannotBeReusedAsTextOrOtherAudioTarget() {
        Long id = presenting(userId);
        UUID request = UUID.randomUUID();
        audio.presentation(userId, id, request, file("presentation"), 100);
        var q = interview.next(userId, id).question();
        fails(
                PtErrorCode.REQUEST_CONFLICT,
                () -> tx.submitText(userId, id, q.questionId(), text(request, "답변")));
        fails(
                PtErrorCode.REQUEST_CONFLICT,
                () -> audio.answer(userId, id, q.questionId(), request, file("presentation"), 100));
    }

    @Test
    void unauthorizedAudioDoesNotCallWhisper() {
        Long id = presenting(userId);
        fails(
                PtErrorCode.SESSION_NOT_FOUND,
                () -> audio.presentation(otherId, id, UUID.randomUUID(), file("audio"), 100));
        verifyNoInteractions(whisper);
    }

    @Test
    void cancelDuringSttDiscardsTranscriptAndUnblocksConnection() throws Exception {
        Long id = ready(userId);
        var q = interview.next(userId, id).question();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(
                i -> {
                    entered.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return "취소 이후 전사";
                })
                .when(whisper)
                .transcribe(any());
        try (var pool = Executors.newFixedThreadPool(1)) {
            var pending =
                    pool.submit(
                            () -> {
                                try {
                                    audio.answer(
                                            userId,
                                            id,
                                            q.questionId(),
                                            UUID.randomUUID(),
                                            file("audio"),
                                            30);
                                    return null;
                                } catch (BusinessException e) {
                                    return e.getErrorCode();
                                }
                            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                fails(
                        PtErrorCode.REQUEST_IN_PROGRESS,
                        () ->
                                tx.submitText(
                                        userId,
                                        id,
                                        q.questionId(),
                                        text(UUID.randomUUID(), "경쟁 답변")));
                progress.cancel(userId, id);
            } finally {
                release.countDown();
            }
            assertEquals(PtErrorCode.SESSION_NOT_IN_PROGRESS, pending.get(10, TimeUnit.SECONDS));
        }
        assertEquals(0, answers.count());
    }

    @Test
    void expiryClosesOnlyExpiredGuestsAndKeepsAccessErrorsStable() {
        Long guest = ready(guestId);
        Long member = ready(userId);
        assertFalse(expiry.expire(guest));
        clock.now = START.plusSeconds(86400);
        expiryJob.expireGuests();
        assertEquals(PtPhase.CANCELED, details.findById(guest).orElseThrow().getPhase());
        assertNotNull(details.findById(guest).orElseThrow().getRetentionClosedAt());
        assertNull(details.findById(member).orElseThrow().getRetentionClosedAt());
        assertFalse(expiry.expire(guest));
        assertFalse(expiry.expire(member));
        fails(PtErrorCode.SESSION_EXPIRED, () -> tx.current(guestId, guest));
        fails(PtErrorCode.SESSION_EXPIRED, () -> create.get(guestId, guest));
    }

    @Test
    void expiredCompletedGuestKeepsCompletionHistoryButCannotRead() {
        Long id = ready(guestId);
        for (int n = 0; n < 6; n++) {
            var q = interview.next(guestId, id).question();
            tx.submitText(guestId, id, q.questionId(), text(UUID.randomUUID(), "답변"));
        }
        clock.now = START.plusSeconds(86400);
        assertTrue(expiry.expire(id));
        assertEquals(SessionStatus.COMPLETED, parents.findById(id).orElseThrow().getStatus());
        assertEquals(PtPhase.COMPLETED, details.findById(id).orElseThrow().getPhase());
        fails(PtErrorCode.SESSION_EXPIRED, () -> tx.evaluationInput(guestId, id));
    }

    @Test
    void commonCancelLocksPtRowAndPreservesCommittedPresentation() throws Exception {
        Long id = presenting(userId);
        UUID requestId = UUID.randomUUID();
        String transcript = "동시에 제출된 발표문도 취소 이후 보존되어야 합니다.";
        var writerHasLock = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        var cancelSelectStarted = new CountDownLatch(1);
        var lockingSql = new java.util.concurrent.atomic.AtomicReference<String>();
        probe.observer = sql -> {
            String normalized = sql.toLowerCase(Locale.ROOT);
            if (Thread.currentThread().getName().equals("pt-cancel-regression")
                    && normalized.startsWith("select")
                    && normalized.contains("from pt_sessions")) {
                lockingSql.set(normalized);
                cancelSelectStarted.countDown();
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            // 상세 행만 잠근 독립 트랜잭션으로 미커밋 발표 데이터를 만든다.
            // 부모 잠금과 별개로 리스너 자체의 상세 행 잠금 대기를 검증한다.
            var writer = pool.submit(() -> {
                inTx(() -> {
                    var detail = details.findByIdForUpdate(id).orElseThrow();
                    detail.submitTextPresentation(requestId, transcript, START);
                    details.flush();
                    writerHasLock.countDown();
                    try {
                        assertTrue(releaseWriter.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                });
            });
            assertTrue(writerHasLock.await(10, TimeUnit.SECONDS));
            var cancellation = pool.submit(() -> {
                Thread.currentThread().setName("pt-cancel-regression");
                return legacy.cancelSession(userId, id);
            });
            try {
                assertTrue(cancelSelectStarted.await(10, TimeUnit.SECONDS));
                assertTrue(lockingSql.get().contains("for update"), lockingSql.get());
                assertThrows(TimeoutException.class,
                        () -> cancellation.get(200, TimeUnit.MILLISECONDS));
            } finally {
                releaseWriter.countDown();
            }
            writer.get(10, TimeUnit.SECONDS);
            cancellation.get(10, TimeUnit.SECONDS);
        } finally {
            releaseWriter.countDown();
            probe.observer = sql -> {};
        }
        var saved = details.findById(id).orElseThrow();
        assertEquals(PtPhase.CANCELED, saved.getPhase());
        assertEquals(SessionStatus.CANCELED, parents.findById(id).orElseThrow().getStatus());
        assertEquals(transcript, saved.getPresentationTranscript());
        assertEquals(requestId.toString(), saved.getPresentationRequestId());
        assertEquals(START, saved.getPresentationSubmittedAt());
        assertEquals(AnswerType.TEXT, saved.getPresentationInputType());
    }

    @Test
    void commonCancelAndPtDetailRollbackTogether() {
        Long id = ready(userId);
        assertThrows(IllegalStateException.class, () -> inTx(() -> {
            legacy.cancelSession(userId, id);
            throw new IllegalStateException("전체 트랜잭션 롤백");
        }));
        assertEquals(SessionStatus.IN_PROGRESS, parents.findById(id).orElseThrow().getStatus());
        var saved = details.findById(id).orElseThrow();
        assertEquals(PtPhase.GENERATING, saved.getPhase());
        assertNotNull(saved.getPresentationTranscript());
    }

    @Test
    void commonCancelKeepsPresentationAndClearsOperationOwnership() {
        Long id = ready(userId);
        tx.prepareNext(userId, id);
        assertNotNull(details.findById(id).orElseThrow().getOperationToken());
        legacy.cancelSession(userId, id);
        var saved = details.findById(id).orElseThrow();
        assertEquals(PtPhase.CANCELED, saved.getPhase());
        assertNotNull(saved.getPresentationTranscript());
        assertNotNull(saved.getPresentationRequestId());
        assertNotNull(saved.getPresentationSubmittedAt());
        assertNull(saved.getOperationToken());
        assertNull(saved.getOperationKind());
        assertNull(saved.getOperationStartedAt());
    }
}
