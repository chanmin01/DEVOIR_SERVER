package com.benly.pt.service;

import com.benly.question.client.ClaudeClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaudePtQuestionGenerator implements PtQuestionGenerator {
    private final ClaudeClient claude;

    @Override
    public String generate(PtGenerationPlan plan) {
        return claude.generatePtQuestion(plan.context(), plan.mainNumber(), plan.followUpNumber());
    }
}