package com.benly.pt.service;

import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.exception.PtErrorCode;
import com.benly.question.client.WhisperClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PtAudioService {
    private final PtInterviewTx tx;
    private final WhisperClient whisper;

    public PtProgressResponse presentation(
            Long userId, Long sessionId, UUID requestId, MultipartFile file, Integer durationSec) {
        String hash = PtPayloads.audioHash(file, durationSec, true);
        PtAudioPlan plan = tx.preparePresentationAudio(userId, sessionId, requestId, hash);
        if (plan.presentationReplay() != null) return plan.presentationReplay();
        try {
            String text = whisper.transcribe(file);
            return tx.finishPresentationAudio(
                    userId, sessionId, requestId, hash, durationSec, plan, text);
        } catch (RuntimeException e) {
            tx.abandon(userId, sessionId, plan.operationToken());
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(PtErrorCode.TRANSCRIPTION_FAILED);
        }
    }

    public PtAnswerResponse answer(
            Long userId,
            Long sessionId,
            Long questionId,
            UUID requestId,
            MultipartFile file,
            Integer durationSec) {
        String hash = PtPayloads.audioHash(file, durationSec, false);
        PtAudioPlan plan = tx.prepareAnswerAudio(userId, sessionId, questionId, requestId, hash);
        if (plan.answerReplay() != null) return plan.answerReplay();
        try {
            String text = whisper.transcribe(file);
            return tx.finishAnswerAudio(
                    userId, sessionId, questionId, requestId, hash, durationSec, plan, text);
        } catch (RuntimeException e) {
            tx.abandon(userId, sessionId, plan.operationToken());
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(PtErrorCode.TRANSCRIPTION_FAILED);
        }
    }
}