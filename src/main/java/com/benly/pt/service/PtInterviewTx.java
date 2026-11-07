package com.benly.pt.service;

import com.benly.feedback.event.PresentationSubmittedEvent;
import com.benly.feedback.event.TopicFinishedEvent;
import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.policy.PtInterviewRules;
import com.benly.pt.repository.*;
import com.benly.question.entity.*;
import com.benly.question.repository.AnswerRepository;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.entity.SessionStatus;
import com.benly.session.repository.SessionRepository;
import com.benly.user.repository.UserRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class PtInterviewTx {
    private final PtSessionRepository details;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final PtQuestionTurnRepository turns;
    private final PtAudioReceiptRepository receipts;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final PtContentCodec codec;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PtInterviewTx(
            PtSessionRepository details,
            SessionRepository sessions,
            UserRepository users,
            PtQuestionTurnRepository turns,
            PtAudioReceiptRepository receipts,
            QuestionRepository questions,
            AnswerRepository answers,
            PtContentCodec codec,
            ObjectMapper mapper,
            ApplicationEventPublisher events,
            @Qualifier("ptClock") Clock clock) {
        this.details = details;
        this.sessions = sessions;
        this.users = users;
        this.turns = turns;
        this.receipts = receipts;
        this.questions = questions;
        this.answers = answers;
        this.codec = codec;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PtInterviewProgressResponse current(Long userId, Long sessionId) {
        return view(owned(userId, sessionId, false));
    }

    public PtGenerationPlan prepareNext(Long userId, Long sessionId) {
        PtSession detail = owned(userId, sessionId, true);
        rejectCanceled(detail);
        List<PtQuestionTurn> list = ordered(sessionId);
        if (detail.getPhase() == PtPhase.COMPLETED || list.stream().anyMatch(t -> !t.answered())) {
            return new PtGenerationPlan(null, 0, 0, 0, null, view(detail));
        }
        running(detail);
        if (detail.getPhase() != PtPhase.GENERATING
                || detail.getPresentationTranscript() == null
                || list.size() >= PtInterviewRules.TOTAL_QUESTION_COUNT) {
            throw fail(PtErrorCode.INVALID_PHASE);
        }
        UUID attempt = detail.claimOperation(PtOperationKind.GENERATE_QUESTION, clock.instant());
        int ordinal = list.size() + 1;
        return new PtGenerationPlan(
                attempt,
                ordinal,
                (ordinal - 1) / 3 + 1,
                (ordinal - 1) % 3,
                prompt(detail, list),
                null);
    }

    public PtInterviewProgressResponse finishGeneration(
            Long userId, Long sessionId, PtGenerationPlan plan, String content) {
        PtSession detail = owned(userId, sessionId, true);
        running(detail);
        detail.requireOperation(plan.operationToken(), clock.instant());
        if (content == null || content.isBlank() || content.length() > 1500) {
            throw fail(PtErrorCode.GENERATION_FAILED);
        }
        List<PtQuestionTurn> list = ordered(sessionId);
        if (list.size() + 1 != plan.ordinal()
                || list.stream().anyMatch(t -> !t.answered())
                || list.stream()
                .anyMatch(
                        t ->
                                t.getQuestion()
                                        .getContent()
                                        .strip()
                                        .equals(content.strip()))) {
            throw fail(PtErrorCode.GENERATION_FAILED);
        }
        Question question;
        if (plan.followUpNumber() == 0) {
            question =
                    Question.createMain(
                            detail.getSession(),
                            plan.mainNumber(),
                            content.strip(),
                            QuestionSourceType.CLAUDE);
        } else {
            int mainOrdinal = (plan.mainNumber() - 1) * 3 + 1;
            Question parent =
                    list.stream()
                            .filter(t -> t.getOrdinal() == mainOrdinal)
                            .findFirst()
                            .orElseThrow(() -> fail(PtErrorCode.INVALID_PHASE))
                            .getQuestion();
            question =
                    Question.createFollowUp(
                            detail.getSession(),
                            parent,
                            plan.followUpNumber(),
                            content.strip(),
                            null,
                            QuestionSourceType.CLAUDE);
        }
        questions.saveAndFlush(question);
        turns.saveAndFlush(PtQuestionTurn.create(detail, question, plan.ordinal()));
        detail.clearOperation();
        detail.openQuestion();
        return view(detail);
    }

    public PtAnswerResponse submitText(
            Long userId, Long sessionId, Long questionId, PtAnswerRequest request) {
        if (request == null || request.requestId() == null) throw fail(PtErrorCode.INVALID_REQUEST);
        String text = validateText(request.transcript(), PtErrorCode.INVALID_REQUEST);
        PtSession detail = owned(userId, sessionId, true);
        rejectCanceled(detail);
        PtQuestionTurn turn = turn(sessionId, questionId);
        String hash = PtPayloads.textHash(text);
        if (receipts.findByPtSession_IdAndRequestId(sessionId, request.requestId().toString())
                .isPresent()) {
            throw fail(PtErrorCode.REQUEST_CONFLICT);
        }
        PtAnswerResponse replay =
                replayAnswer(detail, turn, request.requestId(), hash, AnswerType.TEXT);
        if (replay != null) return replay;
        running(detail);
        detail.requireNoActiveOperation(clock.instant());
        return saveAnswer(detail, turn, request.requestId(), hash, text, AnswerType.TEXT, null);
    }

    public PtAudioPlan preparePresentationAudio(
            Long userId, Long sessionId, UUID requestId, String hash) {
        if (requestId == null) throw fail(PtErrorCode.INVALID_REQUEST);
        PtSession detail = owned(userId, sessionId, true);
        rejectCanceled(detail);
        if (detail.getPresentationRequestId() != null) {
            if (!detail.getPresentationRequestId().equals(requestId.toString())) {
                throw fail(PtErrorCode.PRESENTATION_ALREADY_SUBMITTED);
            }
            if (detail.getPresentationInputType() != AnswerType.AUDIO
                    || !Objects.equals(detail.getPresentationPayloadHash(), hash)) {
                throw fail(PtErrorCode.REQUEST_CONFLICT);
            }
            return new PtAudioPlan(
                    null, null, PtProgressResponse.from(detail, clock.instant()), null);
        }
        running(detail);
        Instant admittedAt = audioAdmission(detail, requestId, null, hash);
        detail.requirePresentationAdmission(admittedAt);
        UUID attempt = detail.claimOperation(PtOperationKind.PRESENTATION_AUDIO, clock.instant());
        return new PtAudioPlan(attempt, admittedAt, null, null);
    }

    public PtProgressResponse finishPresentationAudio(
            Long userId,
            Long sessionId,
            UUID requestId,
            String hash,
            int durationSec,
            PtAudioPlan plan,
            String transcript) {
        PtSession detail = owned(userId, sessionId, true);
        running(detail);
        detail.requireOperation(plan.operationToken(), clock.instant());
        String text = validateText(transcript, PtErrorCode.TRANSCRIPTION_FAILED);
        detail.submitAudioPresentation(
                requestId, text, hash, durationSec, plan.admittedAt(), clock.instant());
        detail.clearOperation();
        events.publishEvent(new PresentationSubmittedEvent(sessionId));
        return PtProgressResponse.from(detail, clock.instant());
    }

    public PtAudioPlan prepareAnswerAudio(
            Long userId, Long sessionId, Long questionId, UUID requestId, String hash) {
        if (requestId == null) throw fail(PtErrorCode.INVALID_REQUEST);
        PtSession detail = owned(userId, sessionId, true);
        rejectCanceled(detail);
        PtQuestionTurn turn = turn(sessionId, questionId);
        PtAnswerResponse replay = replayAnswer(detail, turn, requestId, hash, AnswerType.AUDIO);
        if (replay != null) return new PtAudioPlan(null, null, null, replay);
        running(detail);
        requireCurrent(detail, turn);
        Instant admittedAt = audioAdmission(detail, requestId, questionId, hash);
        UUID attempt = detail.claimOperation(PtOperationKind.ANSWER_AUDIO, clock.instant());
        return new PtAudioPlan(attempt, admittedAt, null, null);
    }

    public PtAnswerResponse finishAnswerAudio(
            Long userId,
            Long sessionId,
            Long questionId,
            UUID requestId,
            String hash,
            int durationSec,
            PtAudioPlan plan,
            String transcript) {
        PtSession detail = owned(userId, sessionId, true);
        running(detail);
        detail.requireOperation(plan.operationToken(), clock.instant());
        String text = validateText(transcript, PtErrorCode.TRANSCRIPTION_FAILED);
        PtQuestionTurn turn = turn(sessionId, questionId);
        detail.clearOperation();
        return saveAnswer(detail, turn, requestId, hash, text, AnswerType.AUDIO, durationSec);
    }

    public void abandon(Long userId, Long sessionId, UUID token) {
        if (token == null || userId == null) return;
        if (users.findByIdForUpdate(userId).isEmpty()) return;
        if (sessions.findOwnedSessionForUpdate(sessionId, userId).isEmpty()) return;
        details.findById(sessionId)
                .filter(d -> d.operationMatches(token))
                .ifPresent(PtSession::clearOperation);
    }

    @Transactional(readOnly = true)
    public PtEvaluationInputResponse evaluationInput(Long userId, Long sessionId) {
        PtSession detail = owned(userId, sessionId, false);
        if (detail.getPhase() != PtPhase.COMPLETED
                || (detail.getSession().getStatus() != SessionStatus.COMPLETED
                && detail.getSession().getStatus() != SessionStatus.ANALYZING)) {
            throw fail(PtErrorCode.INTERVIEW_NOT_COMPLETED);
        }
        List<PtQuestionTurn> list = ordered(sessionId);
        if (list.size() != 6 || list.stream().anyMatch(t -> !t.answered())) {
            throw fail(PtErrorCode.INTERVIEW_NOT_COMPLETED);
        }
        List<PtEvaluationInputResponse.Turn> entries =
                list.stream()
                        .map(
                                t -> {
                                    Answer answer =
                                            answers.findByQuestionId(t.getQuestion().getId())
                                                    .orElseThrow(
                                                            () ->
                                                                    fail(
                                                                            PtErrorCode
                                                                                    .INTERVIEW_NOT_COMPLETED));
                                    return new PtEvaluationInputResponse.Turn(
                                            questionView(t),
                                            answer.getId(),
                                            answer.getTranscript(),
                                            answer.getInputType(),
                                            answer.getDurationSec());
                                })
                        .toList();
        var task = detail.getTask();
        return new PtEvaluationInputResponse(
                1,
                sessionId,
                detail.getSession().getCompanyName(),
                detail.getSession().getJobTitle(),
                detail.getTrack(),
                new PtTaskResponse(
                        task.getId(),
                        task.getVersion(),
                        task.getCompany().getId(),
                        detail.getSession().getCompanyName(),
                        codec.content(task.getContentJson())),
                new PtEvaluationInputResponse.Presentation(
                        detail.getPresentationTranscript(),
                        detail.getPresentationInputType(),
                        detail.getPresentationDurationSec(),
                        detail.getPresentationSubmittedAt()),
                entries);
    }

    private Instant audioAdmission(PtSession detail, UUID requestId, Long questionId, String hash) {
        Optional<PtAudioReceipt> existing =
                receipts.findByPtSession_IdAndRequestId(detail.getId(), requestId.toString());
        if (existing.isPresent()) {
            PtAudioReceipt receipt = existing.get();
            if (!Objects.equals(questionId, receipt.getQuestionId())
                    || !hash.equals(receipt.getPayloadHash())) {
                throw fail(PtErrorCode.REQUEST_CONFLICT);
            }
            return receipt.getAdmittedAt();
        }
        Instant now = clock.instant();
        if (questionId == null) detail.requirePresentationAdmission(now);
        receipts.saveAndFlush(PtAudioReceipt.create(detail, requestId, questionId, hash, now));
        return now;
    }

    private PtAnswerResponse saveAnswer(
            PtSession detail,
            PtQuestionTurn turn,
            UUID requestId,
            String hash,
            String text,
            AnswerType type,
            Integer duration) {
        requireCurrent(detail, turn);
        requireUnusedRequest(detail.getId(), turn, requestId);
        if (answers.existsByQuestionId(turn.getQuestion().getId()))
            throw fail(PtErrorCode.ANSWER_CONFLICT);
        Answer answer =
                type == AnswerType.TEXT
                        ? Answer.createText(turn.getQuestion(), text)
                        : Answer.createAudio(turn.getQuestion(), text, duration);
        answers.saveAndFlush(answer);
        turn.recordAnswer(requestId, hash);
        turns.flush();
        detail.finishAnswer(turn.getOrdinal() == PtInterviewRules.TOTAL_QUESTION_COUNT);
        if (turn.followUpNumber() == 2) {
            Long mainQuestionId = turn.getQuestion().getParent().getId();
            events.publishEvent(new TopicFinishedEvent(detail.getId(), mainQuestionId));
        }
        return answerView(detail, answer);
    }

    private PtAnswerResponse replayAnswer(
            PtSession detail, PtQuestionTurn turn, UUID requestId, String hash, AnswerType type) {
        requireUnusedRequest(detail.getId(), turn, requestId);
        if (!turn.answered()) return null;
        Answer saved =
                answers.findByQuestionId(turn.getQuestion().getId())
                        .orElseThrow(() -> fail(PtErrorCode.ANSWER_CONFLICT));
        if (!requestId.toString().equals(turn.getAnswerRequestId())
                || !hash.equals(turn.getAnswerPayloadHash())
                || saved.getInputType() != type) {
            throw fail(PtErrorCode.ANSWER_CONFLICT);
        }
        return answerView(detail, saved);
    }

    private void requireUnusedRequest(Long sessionId, PtQuestionTurn target, UUID requestId) {
        turns.findByPtSession_IdAndAnswerRequestId(sessionId, requestId.toString())
                .filter(other -> !other.getId().equals(target.getId()))
                .ifPresent(
                        other -> {
                            throw fail(PtErrorCode.REQUEST_CONFLICT);
                        });
    }

    private void requireCurrent(PtSession detail, PtQuestionTurn target) {
        if (detail.getPhase() != PtPhase.QUESTIONING || target.answered())
            throw fail(PtErrorCode.INVALID_PHASE);
        PtQuestionTurn expected =
                ordered(detail.getId()).stream()
                        .filter(t -> !t.answered())
                        .findFirst()
                        .orElseThrow(() -> fail(PtErrorCode.INVALID_PHASE));
        if (!expected.getId().equals(target.getId())) throw fail(PtErrorCode.INVALID_PHASE);
    }

    private PtQuestionTurn turn(Long sessionId, Long questionId) {
        return turns.findByPtSession_IdAndQuestion_Id(sessionId, questionId)
                .orElseThrow(() -> fail(PtErrorCode.QUESTION_NOT_FOUND));
    }

    private List<PtQuestionTurn> ordered(Long sessionId) {
        return turns.findByPtSession_IdOrderByOrdinalAsc(sessionId);
    }

    private PtInterviewProgressResponse view(PtSession detail) {
        List<PtQuestionTurn> list = ordered(detail.getId());
        int answered = (int) list.stream().filter(PtQuestionTurn::answered).count();
        PtPhase phase =
                detail.getSession().getStatus() == SessionStatus.CANCELED
                        ? PtPhase.CANCELED
                        : detail.getPhase();
        PtQuestionResponse question =
                phase == PtPhase.QUESTIONING
                        ? list.stream()
                        .filter(t -> !t.answered())
                        .findFirst()
                        .map(this::questionView)
                        .orElse(null)
                        : null;
        PtNextAction action =
                switch (phase) {
                    case PREPARING ->
                            detail.getTrack() == null
                                    ? PtNextAction.SELECT_TRACK
                                    : PtNextAction.START_PRESENTATION;
                    case PRESENTING -> PtNextAction.SUBMIT_PRESENTATION;
                    case GENERATING -> PtNextAction.GENERATE_QUESTION;
                    case QUESTIONING -> PtNextAction.ANSWER;
                    case COMPLETED -> PtNextAction.FINISH;
                    case CANCELED -> PtNextAction.NONE;
                };
        return new PtInterviewProgressResponse(
                detail.getId(),
                detail.getSession().getStatus(),
                phase,
                answered,
                6,
                detail.isOperationActive(clock.instant()),
                question,
                action);
    }

    private PtQuestionResponse questionView(PtQuestionTurn turn) {
        return new PtQuestionResponse(
                turn.getQuestion().getId(),
                turn.getOrdinal(),
                turn.mainNumber(),
                turn.followUpNumber(),
                turn.getQuestion().getType(),
                turn.getQuestion().getContent());
    }

    private PtAnswerResponse answerView(PtSession detail, Answer answer) {
        return new PtAnswerResponse(
                answer.getId(),
                answer.getQuestion().getId(),
                answer.getInputType(),
                answer.getTranscript(),
                answer.getDurationSec(),
                view(detail));
    }

    private PtSession owned(Long userId, Long sessionId, boolean lock) {
        if (userId == null) throw fail(PtErrorCode.USER_UNAVAILABLE);
        var user =
                lock ? users.findByIdForUpdate(userId) : users.findByIdAndDeletedAtIsNull(userId);
        if (user.isEmpty() || user.get().isDeleted()) throw fail(PtErrorCode.USER_UNAVAILABLE);
        PtSession detail;
        if (lock) {
            sessions.findOwnedSessionForUpdate(sessionId, userId)
                    .orElseThrow(() -> fail(PtErrorCode.SESSION_NOT_FOUND));
            detail =
                    details.findById(sessionId)
                            .orElseThrow(() -> fail(PtErrorCode.SESSION_NOT_FOUND));
        } else {
            detail =
                    details.findByIdAndSession_User_IdAndSession_DeletedAtIsNull(sessionId, userId)
                            .orElseThrow(() -> fail(PtErrorCode.SESSION_NOT_FOUND));
        }
        if (detail.getExpiresAt() != null && !detail.getExpiresAt().isAfter(clock.instant())) {
            throw fail(PtErrorCode.SESSION_EXPIRED);
        }
        return detail;
    }

    private void running(PtSession detail) {
        if (detail.getSession().getStatus() != SessionStatus.IN_PROGRESS) {
            throw fail(PtErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    private void rejectCanceled(PtSession detail) {
        if (detail.getSession().getStatus() == SessionStatus.CANCELED) {
            throw fail(PtErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    private String validateText(String value, PtErrorCode error) {
        if (value == null || value.isBlank() || value.length() > 30000) throw fail(error);
        return value.strip();
    }

    private String prompt(PtSession detail, List<PtQuestionTurn> list) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (PtQuestionTurn t : list) {
            Answer answer =
                    answers.findByQuestionId(t.getQuestion().getId())
                            .orElseThrow(() -> fail(PtErrorCode.INVALID_PHASE));
            history.add(
                    Map.of(
                            "ordinal",
                            t.getOrdinal(),
                            "question",
                            t.getQuestion().getContent(),
                            "answer",
                            clipped(answer.getTranscript(), 8000)));
        }
        return mapper.writeValueAsString(
                Map.of(
                        "company", detail.getSession().getCompanyName(),
                        "jobRole", Objects.toString(detail.getSession().getJobTitle(), "미지정"),
                        "track", detail.getTrack().name(),
                        "task", clipped(detail.getTask().getContentJson(), 16000),
                        "presentation", clipped(detail.getPresentationTranscript(), 16000),
                        "history", history));
    }

    private String clipped(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + " [이후 생략]";
    }

    private static BusinessException fail(PtErrorCode code) {
        return new BusinessException(code);
    }
}