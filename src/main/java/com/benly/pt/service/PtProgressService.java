package com.benly.pt.service;

import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.PtProgressResponse;
import com.benly.pt.dto.PtTextPresentationRequest;
import com.benly.pt.dto.PtTrackRequest;
import com.benly.pt.entity.PtSession;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.entity.SessionStatus;
import com.benly.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class PtProgressService {

    private final PtSessionRepository details;
    private final UserRepository users;
    private final Clock clock;

    public PtProgressService(
            PtSessionRepository details,
            UserRepository users,
            @Qualifier("ptClock") Clock clock
    ) {
        this.details = details;
        this.users = users;
        this.clock = clock;
    }

    public PtProgressResponse selectTrack(
            Long userId,
            Long sessionId,
            PtTrackRequest request
    ) {
        if (request == null || request.track() == null) {
            throw error(PtErrorCode.INVALID_REQUEST);
        }

        PtSession detail = ownedForUpdate(userId, sessionId);
        requireRunning(detail);

        detail.selectTrack(request.track());

        return PtProgressResponse.from(detail, clock.instant());
    }

    public PtProgressResponse startPresentation(
            Long userId,
            Long sessionId
    ) {
        PtSession detail = ownedForUpdate(userId, sessionId);
        requireRunning(detail);

        detail.startPresentation(clock.instant());

        return PtProgressResponse.from(detail, clock.instant());
    }

    public PtProgressResponse submitText(
            Long userId,
            Long sessionId,
            PtTextPresentationRequest request
    ) {
        if (request == null
                || request.requestId() == null
                || request.transcript() == null
                || request.transcript().isBlank()
                || request.transcript().length() > 30000) {
            throw error(PtErrorCode.INVALID_REQUEST);
        }

        PtSession detail = ownedForUpdate(userId, sessionId);
        requireRunning(detail);

        detail.submitTextPresentation(
                request.requestId(),
                request.transcript().strip(),
                clock.instant()
        );

        return PtProgressResponse.from(detail, clock.instant());
    }

    public PtProgressResponse cancel(
            Long userId,
            Long sessionId
    ) {
        PtSession detail = ownedForUpdate(userId, sessionId);

        SessionStatus status = detail.getSession().getStatus();

        if (status != SessionStatus.IN_PROGRESS
                && status != SessionStatus.CANCELED) {
            throw error(PtErrorCode.CANNOT_CANCEL);
        }

        detail.cancel();
        detail.getSession().markCanceled();

        return PtProgressResponse.from(detail, clock.instant());
    }

    private PtSession ownedForUpdate(
            Long userId,
            Long sessionId
    ) {
        if (userId == null) {
            throw error(PtErrorCode.USER_UNAVAILABLE);
        }

        // 생성·회원 탈퇴와 같은 순서로 사용자 행부터 잠근다.
        users.findByIdForUpdate(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() ->
                        error(PtErrorCode.USER_UNAVAILABLE)
                );

        // 기존 공통 취소 API와 같은 Session 행을 잠근다.
        details.findOwnedSessionForUpdate(sessionId, userId)
                .orElseThrow(() ->
                        error(PtErrorCode.SESSION_NOT_FOUND)
                );

        PtSession detail = details.findById(sessionId)
                .orElseThrow(() ->
                        error(PtErrorCode.SESSION_NOT_FOUND)
                );

        if (detail.getExpiresAt() != null
                && !detail.getExpiresAt().isAfter(clock.instant())) {
            throw error(PtErrorCode.SESSION_EXPIRED);
        }

        return detail;
    }

    private void requireRunning(PtSession detail) {
        if (detail.getSession().getStatus()
                != SessionStatus.IN_PROGRESS) {
            throw error(PtErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    private static BusinessException error(
            PtErrorCode code
    ) {
        return new BusinessException(code);
    }
}