package com.benly.pt;

import com.benly.archive.entity.Company;
import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.PtProgressResponse;
import com.benly.pt.dto.PtTaskSettingsResponse;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.session.entity.*;
import com.benly.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PtTaskSettingsTest {

    private static final Instant NOW =
            Instant.parse("2026-11-23T00:00:00Z");

    private final Company company =
            Company.ofUser("테스트기업", "테스트기업");

    private PtTask configured(List<PtTrack> tracks) {
        return PtTask.approvedExercise(
                company,
                CompanyType.BIG_TECH_SW,
                "TEST",
                "{}",
                PtTaskType.JOB_PROBLEM_SOLVING,
                "플랫폼",
                tracks,
                List.of(10, 20),
                180
        );
    }

    private PtSession prepare(
            PtTask task,
            int minutes
    ) {
        var parent = Session.create(
                User.of("settings", "회원"),
                CompanyType.BIG_TECH_SW.name(),
                InterviewStage.TECHNICAL.name(),
                "플랫폼",
                company.getName()
        );

        parent.markInProgress();

        return PtSession.prepare(
                parent,
                task,
                UUID.randomUUID(),
                minutes,
                NOW,
                null
        );
    }

    @Test
    void legacyTaskStillRequiresTrackAndFiveMinutePresentation() {
        var task = PtTask.approvedSample(
                company,
                CompanyType.FINANCE_IT,
                "OLD",
                "{}"
        );

        var detail = prepare(task, 15);

        assertEquals(
                PtNextAction.SELECT_TRACK,
                PtProgressResponse.from(detail, NOW)
                        .nextAction()
        );

        var error = assertThrows(
                BusinessException.class,
                () -> detail.startPresentation(NOW)
        );

        assertEquals(
                PtErrorCode.TRACK_REQUIRED,
                error.getErrorCode()
        );

        detail.selectTrack(PtTrack.AI);
        detail.startPresentation(NOW);

        assertEquals(
                NOW.plusSeconds(300),
                detail.getPresentationDeadline()
        );
    }

    @Test
    void noTrackTaskStartsImmediatelyAndUsesItsOwnDuration() {
        var detail = prepare(
                configured(List.of()),
                10
        );

        assertEquals(
                PtNextAction.START_PRESENTATION,
                PtProgressResponse.from(detail, NOW)
                        .nextAction()
        );

        detail.startPresentation(NOW);

        assertNull(detail.getTrack());

        assertEquals(
                NOW.plusSeconds(180),
                detail.getPresentationDeadline()
        );
    }

    @Test
    void lateStartDoesNotExtendDeadline() {
        var detail = prepare(
                configured(List.of()),
                10
        );

        detail.startPresentation(
                NOW.plusSeconds(650)
        );

        assertEquals(
                NOW.plusSeconds(600),
                detail.getPresentationStartedAt()
        );

        assertEquals(
                NOW.plusSeconds(780),
                detail.getPresentationDeadline()
        );
    }

    @Test
    void invalidTrackAndPreparationTimeAreRejected() {
        var task = configured(
                List.of(PtTrack.DATA)
        );

        var detail = prepare(task, 10);

        var error = assertThrows(
                BusinessException.class,
                () -> detail.selectTrack(PtTrack.AI)
        );

        assertEquals(
                PtErrorCode.TRACK_NOT_ALLOWED,
                error.getErrorCode()
        );

        assertThrows(
                BusinessException.class,
                () -> prepare(task, 15)
        );

        detail.selectTrack(PtTrack.DATA);

        assertFalse(
                detail.needsTrackSelection()
        );
    }

    @Test
    void submissionGraceStillAppliesToTaskSpecificDeadline() {
        var detail = prepare(
                configured(List.of()),
                10
        );

        detail.startPresentation(NOW);

        detail.requirePresentationAdmission(
                NOW.plusSeconds(240)
        );

        var error = assertThrows(
                BusinessException.class,
                () -> detail.requirePresentationAdmission(
                        NOW.plusSeconds(241)
                )
        );

        assertEquals(
                PtErrorCode.PRESENTATION_SUBMISSION_EXPIRED,
                error.getErrorCode()
        );
    }

    @Test
    void settingsExposeTargetRoleAndPracticeNotice() {
        var settings = PtTaskSettingsResponse.from(
                configured(List.of())
        );

        assertFalse(
                settings.trackSelectionRequired()
        );

        assertEquals(
                List.of(10, 20),
                settings.preparationMinutes()
        );

        assertEquals(
                "플랫폼",
                settings.targetJobRole()
        );

        assertTrue(
                settings.practiceNotice()
                        .contains("연습용")
        );
    }
}