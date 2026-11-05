package com.benly.pt.service;

import com.benly.archive.entity.InterviewFormat;
import com.benly.archive.repository.CompanyRepository;
import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.policy.PtInterviewRules;
import com.benly.pt.repository.*;
import com.benly.session.entity.*;
import com.benly.session.repository.SessionRepository;
import com.benly.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class PtSessionService {

    private final PtTaskRepository tasks;
    private final PtSessionRepository details;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PtContentCodec codec;
    private final Clock clock;

    public PtSessionService(
            PtTaskRepository tasks,
            PtSessionRepository details,
            SessionRepository sessions,
            UserRepository users,
            CompanyRepository companies,
            PtContentCodec codec,
            @Qualifier("ptClock") Clock clock
    ) {
        this.tasks = tasks;
        this.details = details;
        this.sessions = sessions;
        this.users = users;
        this.companies = companies;
        this.codec = codec;
        this.clock = clock;
    }

    @Transactional
    public PtSessionResponse create(
            Long userId,
            PtSessionCreateRequest request
    ) {
        if (request.requestId() == null
                || !PtInterviewRules.PREPARATION_MINUTES
                .contains(request.preparationMinutes())) {
            throw new BusinessException(
                    PtErrorCode.INVALID_REQUEST
            );
        }

        if (userId == null) {
            throw new BusinessException(
                    PtErrorCode.USER_UNAVAILABLE
            );
        }

        var user = users.findByIdForUpdate(userId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() ->
                        new BusinessException(
                                PtErrorCode.USER_UNAVAILABLE
                        )
                );

        String role =
                request.jobRole() == null
                        || request.jobRole().isBlank()
                        ? null
                        : request.jobRole().strip();

        var existing = details.findByCreationRequestId(
                request.requestId().toString()
        );

        if (existing.isPresent()) {
            var detail = existing.get();
            var session = detail.getSession();

            boolean differentRequest =
                    !session.getUser().getId().equals(userId)
                            || session.isDeleted()
                            || !detail.getTask()
                            .getCompany()
                            .getId()
                            .equals(request.companyId())
                            || detail.getPreparationMinutes()
                            != request.preparationMinutes()
                            || !Objects.equals(
                            session.getJobTitle(),
                            role
                    );

            if (differentRequest) {
                throw new BusinessException(
                        PtErrorCode.REQUEST_CONFLICT
                );
            }

            return response(detail);
        }

        var company = companies.findById(request.companyId())
                .orElseThrow(() ->
                        new BusinessException(
                                PtErrorCode.COMPANY_NOT_FOUND
                        )
                );

        var task = tasks
                .findFirstByCompany_IdAndStatusOrderByIdDesc(
                        company.getId(),
                        PtTaskStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                PtErrorCode.TASK_NOT_AVAILABLE
                        )
                );

        Instant now = clock.instant();

        var session = Session.create(
                user,
                task.getCompanyType().name(),
                InterviewStage.TECHNICAL.name(),
                role,
                company.getName()
        );

        session.markInProgress();
        sessions.saveAndFlush(session);

        var detail = PtSession.prepare(
                session,
                task,
                request.requestId(),
                request.preparationMinutes(),
                now,
                user.isGuest()
                        ? now.plus(PtInterviewRules.GUEST_RETENTION)
                        : null
        );

        return response(details.saveAndFlush(detail));
    }

    public PtSessionResponse get(
            Long userId,
            Long sessionId
    ) {
        if (userId == null
                || users.findByIdAndDeletedAtIsNull(userId)
                .isEmpty()) {
            throw new BusinessException(
                    PtErrorCode.USER_UNAVAILABLE
            );
        }

        var detail = details
                .findByIdAndSession_User_IdAndSession_DeletedAtIsNull(
                        sessionId,
                        userId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                PtErrorCode.SESSION_NOT_FOUND
                        )
                );

        return response(detail);
    }

    private PtSessionResponse response(PtSession detail) {
        Instant now = clock.instant();

        if (detail.getExpiresAt() != null
                && !detail.getExpiresAt().isAfter(now)) {
            throw new BusinessException(
                    PtErrorCode.SESSION_EXPIRED
            );
        }

        var session = detail.getSession();

        var phase =
                session.getStatus() == SessionStatus.CANCELED
                        ? PtPhase.CANCELED
                        : detail.getPhase();

        var task = detail.getTask();

        var taskResponse = new PtTaskResponse(
                task.getId(),
                task.getVersion(),
                task.getCompany().getId(),
                session.getCompanyName(),
                codec.content(task.getContentJson())
        );

        var progress = PtProgressResponse.from(detail, now);

        return new PtSessionResponse(
                session.getId(),
                InterviewFormat.PT,
                session.getStatus(),
                phase,
                session.getJobTitle(),
                taskResponse,
                detail.getPreparationMinutes(),
                detail.getPreparationStartedAt(),
                detail.getPreparationDeadline(),
                now,
                detail.getExpiresAt(),
                progress.track(),
                progress.presentationStartedAt(),
                progress.presentationDeadline(),
                progress.presentation(),
                progress.nextAction()
        );
    }
}