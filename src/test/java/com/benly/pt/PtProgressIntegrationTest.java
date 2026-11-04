package com.benly.pt;

import static org.junit.jupiter.api.Assertions.*;

import com.benly.archive.entity.Company;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.*;
import com.benly.pt.service.*;
import com.benly.question.entity.AnswerType;
import com.benly.session.entity.*;
import com.benly.session.repository.SessionRepository;
import com.benly.session.service.SessionService;
import com.benly.user.entity.User;
import com.benly.user.repository.UserRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringJUnitConfig(PtSessionIntegrationTest.Config.class)
class PtProgressIntegrationTest {
    private static final Instant START = Instant.parse("2026-11-04T04:00:00Z");
    @Autowired PtProgressService progress;
    @Autowired PtSessionService sessions;
    @Autowired PtSessionRepository details;
    @Autowired PtTaskRepository tasks;
    @Autowired SessionRepository parents;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired PtContentCodec codec;
    @Autowired PtSessionIntegrationTest.MutableClock clock;
    @Autowired JpaTransactionManager manager;
    @Autowired SessionService legacy;
    Long companyId, userId, otherId, guestId;

    @BeforeEach
    void setup() {
        clock.now = START;
        tx(
                () -> {
                    details.deleteAll();
                    parents.deleteAll();
                    tasks.deleteAll();
                    companies.deleteAll();
                    users.deleteAll();
                    var company = companies.saveAndFlush(Company.ofUser("기업은행", "기업은행"));
                    companyId = company.getId();
                    userId = users.saveAndFlush(User.of("pr2-member", "회원")).getId();
                    otherId = users.saveAndFlush(User.of("pr2-other", "다른 회원")).getId();
                    guestId = users.saveAndFlush(User.createGuest()).getId();
                    var content =
                            new PtTaskContent(
                                    "가상 과제",
                                    "가상 서비스",
                                    List.of("입력", "추천"),
                                    "문제",
                                    List.of("자료"),
                                    List.of("3개월"),
                                    List.of("연습용"),
                                    Map.of(
                                            PtTrack.SECURITY,
                                            "보안",
                                            PtTrack.UI_UX,
                                            "UX",
                                            PtTrack.AI,
                                            "AI",
                                            PtTrack.DATA,
                                            "데이터"),
                                    List.of("5분 발표"));
                    tasks.saveAndFlush(
                            PtTask.approvedSample(
                                    company,
                                    CompanyType.FINANCE_IT,
                                    "PR2",
                                    codec.content(content)));
                });
    }

    void tx(Runnable action) {
        new TransactionTemplate(manager)
                .execute(
                        status -> {
                            action.run();
                            return null;
                        });
    }

    Long create(Long owner) {
        return sessions.create(
                        owner, new PtSessionCreateRequest(UUID.randomUUID(), companyId, 20, "백엔드"))
                .sessionId();
    }

    Long presenting() {
        Long id = create(userId);
        progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.DATA));
        progress.startPresentation(userId, id);
        return id;
    }

    PtTextPresentationRequest text(UUID id, String value) {
        return new PtTextPresentationRequest(id, value);
    }

    void fails(PtErrorCode code, Runnable action) {
        var e = assertThrows(BusinessException.class, action::run);
        assertEquals(code, e.getErrorCode());
    }

    @Test
    void trackChangesDuringPreparationWithoutResettingTimer() {
        Long id = create(userId);
        Instant deadline = sessions.get(userId, id).preparationDeadline();
        progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.DATA));
        clock.now = START.plusSeconds(30);
        var changed = progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.AI));
        assertEquals(PtTrack.AI, changed.track());
        assertEquals(deadline, changed.preparationDeadline());
        assertEquals(PtTrack.AI, sessions.get(userId, id).track());
        assertEquals("START_PRESENTATION", changed.nextAction());
    }

    @Test
    void rejectsNullTrack() {
        Long id = create(userId);
        fails(
                PtErrorCode.INVALID_REQUEST,
                () -> progress.selectTrack(userId, id, new PtTrackRequest(null)));
    }

    @Test
    void presentationRequiresTrack() {
        Long id = create(userId);
        fails(PtErrorCode.TRACK_REQUIRED, () -> progress.startPresentation(userId, id));
        assertEquals(PtPhase.PREPARING, sessions.get(userId, id).phase());
    }

    @Test
    void earlyStartUsesCurrentTimeAndRepeatedStartDoesNotExtend() {
        Long id = create(userId);
        progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.DATA));
        clock.now = START.plusSeconds(30);
        var first = progress.startPresentation(userId, id);
        clock.now = clock.now.plusSeconds(20);
        var replay = progress.startPresentation(userId, id);
        assertEquals(START.plusSeconds(30), first.presentationStartedAt());
        assertEquals(START.plusSeconds(330), replay.presentationDeadline());
        assertEquals(first.presentationDeadline(), sessions.get(userId, id).presentationDeadline());
    }

    @Test
    void lateStartDoesNotAddPreparationTime() {
        Long id = create(userId);
        progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.DATA));
        clock.now = START.plusSeconds(1230);
        var result = progress.startPresentation(userId, id);
        assertEquals(START.plusSeconds(1200), result.presentationStartedAt());
        assertEquals(START.plusSeconds(1500), result.presentationDeadline());
    }

    @Test
    void trackCannotChangeAfterStart() {
        Long id = presenting();
        fails(
                PtErrorCode.INVALID_PHASE,
                () -> progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.AI)));
        assertEquals(PtTrack.DATA, sessions.get(userId, id).track());
    }

    @Test
    void textSubmissionIsSavedAndVisibleOnReload() {
        Long id = presenting();
        clock.now = START.plusSeconds(100);
        var result = progress.submitText(userId, id, text(UUID.randomUUID(), "  실제 발표 내용입니다.  "));
        assertEquals(PtPhase.GENERATING, result.phase());
        assertEquals("GENERATE_QUESTION", result.nextAction());
        var reloaded = sessions.get(userId, id);
        assertEquals("실제 발표 내용입니다.", reloaded.presentation().transcript());
        assertEquals(AnswerType.TEXT, reloaded.presentation().inputType());
        assertEquals(clock.instant(), reloaded.presentation().submittedAt());
        assertEquals(SessionStatus.IN_PROGRESS, reloaded.status());
    }

    @Test
    void sameSubmissionDoesNotOverwriteTime() {
        Long id = presenting();
        UUID request = UUID.randomUUID();
        var first = progress.submitText(userId, id, text(request, "발표"));
        clock.now = clock.now.plusSeconds(20);
        var replay = progress.submitText(userId, id, text(request, " 발표 "));
        assertEquals(first.presentation(), replay.presentation());
        assertEquals(PtPhase.GENERATING, replay.phase());
    }

    @Test
    void sameRequestDifferentTextIsConflict() {
        Long id = presenting();
        UUID request = UUID.randomUUID();
        progress.submitText(userId, id, text(request, "원문"));
        fails(
                PtErrorCode.REQUEST_CONFLICT,
                () -> progress.submitText(userId, id, text(request, "다른 내용")));
        assertEquals("원문", sessions.get(userId, id).presentation().transcript());
    }

    @Test
    void secondSubmissionWithNewRequestIsRejected() {
        Long id = presenting();
        progress.submitText(userId, id, text(UUID.randomUUID(), "원문"));
        fails(
                PtErrorCode.PRESENTATION_ALREADY_SUBMITTED,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "수정")));
    }

    @Test
    void cannotSubmitBeforeStarting() {
        Long id = create(userId);
        fails(
                PtErrorCode.INVALID_PHASE,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "발표")));
    }

    @Test
    void rejectsEmptyAndOversizedText() {
        Long id = presenting();
        fails(
                PtErrorCode.INVALID_REQUEST,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "  ")));
        fails(
                PtErrorCode.INVALID_REQUEST,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "가".repeat(30001))));
        assertEquals(PtPhase.PRESENTING, sessions.get(userId, id).phase());
    }

    @Test
    void acceptsTranscriptArrivingAfterDeadline() {
        Long id = presenting();
        clock.now = START.plusSeconds(400);
        assertEquals(
                PtPhase.GENERATING,
                progress.submitText(userId, id, text(UUID.randomUUID(), "전송이 늦게 도착한 발표")).phase());
    }

    @Test
    void cannotRestartAfterSubmission() {
        Long id = presenting();
        progress.submitText(userId, id, text(UUID.randomUUID(), "발표"));
        fails(PtErrorCode.INVALID_PHASE, () -> progress.startPresentation(userId, id));
    }

    @ParameterizedTest
    @EnumSource(
            value = PtPhase.class,
            names = {"PREPARING", "PRESENTING", "GENERATING"})
    void cancelsParentAndDetailAndIsIdempotent(PtPhase phase) {
        Long id = create(userId);
        if (phase != PtPhase.PREPARING) {
            progress.selectTrack(userId, id, new PtTrackRequest(PtTrack.DATA));
            progress.startPresentation(userId, id);
        }
        if (phase == PtPhase.GENERATING)
            progress.submitText(userId, id, text(UUID.randomUUID(), "발표"));
        var result = progress.cancel(userId, id);
        assertEquals(PtPhase.CANCELED, result.phase());
        assertEquals(SessionStatus.CANCELED, parents.findById(id).orElseThrow().getStatus());
        assertEquals(PtPhase.CANCELED, details.findById(id).orElseThrow().getPhase());
        assertEquals("NONE", progress.cancel(userId, id).nextAction());
    }

    @Test
    void legacyCancelBlocksFurtherPtWrites() {
        Long id = presenting();
        legacy.cancelSession(userId, id);
        fails(PtErrorCode.SESSION_NOT_IN_PROGRESS, () -> progress.startPresentation(userId, id));
        fails(
                PtErrorCode.SESSION_NOT_IN_PROGRESS,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "발표")));
        assertEquals(PtPhase.CANCELED, progress.cancel(userId, id).phase());
    }

    @Test
    void wrongOwnerCannotReadOrWriteProgress() {
        Long id = presenting();
        fails(
                PtErrorCode.SESSION_NOT_FOUND,
                () -> progress.selectTrack(otherId, id, new PtTrackRequest(PtTrack.AI)));
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> progress.startPresentation(otherId, id));
        fails(
                PtErrorCode.SESSION_NOT_FOUND,
                () -> progress.submitText(otherId, id, text(UUID.randomUUID(), "침입")));
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> progress.cancel(otherId, id));
    }

    @Test
    void completedParentCannotBeCanceledOrChanged() {
        Long id = presenting();
        tx(() -> parents.findById(id).orElseThrow().markCompleted());
        fails(PtErrorCode.CANNOT_CANCEL, () -> progress.cancel(userId, id));
        fails(
                PtErrorCode.SESSION_NOT_IN_PROGRESS,
                () -> progress.submitText(userId, id, text(UUID.randomUUID(), "발표")));
    }

    @Test
    void expiredGuestCannotProgress() {
        Long id = create(guestId);
        clock.now = START.plusSeconds(86400);
        fails(
                PtErrorCode.SESSION_EXPIRED,
                () -> progress.selectTrack(guestId, id, new PtTrackRequest(PtTrack.DATA)));
        fails(PtErrorCode.SESSION_EXPIRED, () -> progress.cancel(guestId, id));
    }

    @Test
    void withdrawnUserCannotProgress() {
        Long id = presenting();
        tx(() -> users.findById(userId).orElseThrow().withdraw());
        fails(PtErrorCode.USER_UNAVAILABLE, () -> progress.startPresentation(userId, id));
        fails(PtErrorCode.USER_UNAVAILABLE, () -> progress.cancel(userId, id));
    }

    @Test
    void generalSessionIsNotTreatedAsPt() {
        final Long[] id = {null};
        tx(
                () -> {
                    var session =
                            Session.create(
                                    users.findById(userId).orElseThrow(),
                                    "SERVICE",
                                    "TECHNICAL",
                                    null,
                                    "회사");
                    session.markInProgress();
                    id[0] = parents.saveAndFlush(session).getId();
                });
        fails(PtErrorCode.SESSION_NOT_FOUND, () -> progress.cancel(userId, id[0]));
        assertEquals(SessionStatus.IN_PROGRESS, parents.findById(id[0]).orElseThrow().getStatus());
    }

    @Test
    void concurrentSubmissionsPersistOnePayload() throws Exception {
        Long id = presenting();
        UUID request = UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> submitAfter(start, id, request, "첫 내용"));
            var b = pool.submit(() -> submitAfter(start, id, request, "둘째 내용"));
            start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(PtPhase.GENERATING, sessions.get(userId, id).phase());
    }

    boolean submitAfter(CountDownLatch start, Long id, UUID request, String text) throws Exception {
        start.await();
        try {
            progress.submitText(userId, id, text(request, text));
            return true;
        } catch (BusinessException e) {
            assertEquals(PtErrorCode.REQUEST_CONFLICT, e.getErrorCode());
            return false;
        }
    }

    @Test
    void concurrentCancelAndSubmissionEndsCanceled() throws Exception {
        Long id = presenting();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var cancel =
                    pool.submit(
                            () -> {
                                start.await();
                                return progress.cancel(userId, id);
                            });
            var submit =
                    pool.submit(
                            () -> {
                                start.await();
                                try {
                                    progress.submitText(userId, id, text(UUID.randomUUID(), "발표"));
                                } catch (BusinessException e) {
                                    assertEquals(
                                            PtErrorCode.SESSION_NOT_IN_PROGRESS, e.getErrorCode());
                                }
                                return null;
                            });
            start.countDown();
            cancel.get(10, TimeUnit.SECONDS);
            submit.get(10, TimeUnit.SECONDS);
        }
        assertEquals(PtPhase.CANCELED, sessions.get(userId, id).phase());
        assertEquals(SessionStatus.CANCELED, parents.findById(id).orElseThrow().getStatus());
    }
}
