package com.benly.pt.service;

import com.benly.global.exception.BusinessException;
import com.benly.pt.dto.*;
import com.benly.pt.exception.PtErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PtInterviewService {
    private final PtInterviewTx tx;
    private final PtQuestionGenerator generator;

    // 외부 AI 호출 중 DB 트랜잭션과 잠금을 유지하지 않는다.
    public PtInterviewProgressResponse next(Long userId, Long sessionId) {
        PtGenerationPlan plan = tx.prepareNext(userId, sessionId);
        if (plan.existing() != null) return plan.existing();
        try {
            String question = generator.generate(plan);
            return tx.finishGeneration(userId, sessionId, plan, question);
        } catch (RuntimeException e) {
            tx.abandon(userId, sessionId, plan.operationToken());
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException(PtErrorCode.GENERATION_FAILED);
        }
    }
}