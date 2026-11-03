package com.benly.question.service;

import com.benly.document.entity.Document;
import com.benly.document.repository.DocumentRepository;
import com.benly.document.storage.PdfTextExtractor;
import com.benly.document.storage.S3StorageService;
import com.benly.question.client.ClaudeClient;
import com.benly.question.entity.QuestionSourceType;
import com.benly.question.entity.SeedQuestion;
import com.benly.question.repository.SeedQuestionRepository;
import com.benly.session.entity.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private final ClaudeClient claudeClient;
    private final SeedQuestionRepository seedQuestionRepository;
    private final DocumentRepository documentRepository;
    private final S3StorageService s3StorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final QuestionGenerationTx generationTx;

    @Async
    public void generate(Long sessionId, String jd, Long docId) {
        Session session = generationTx.markStarted(sessionId).orElse(null);
        if (session == null) {
            log.warn("대기 중에 이미 정리된 세션이라 질문 생성 안 함 sessionId={}", sessionId);
            return;
        }

        try {
            String docText = extractDocumentText(docId);
            GeneratedQuestions generated = generateOrFallback(session, jd, docText);

            boolean saved = generationTx.saveQuestions(sessionId, generated.texts(), generated.sourceType());
            if (!saved) {
                log.warn("생성이 늦게 끝나서 결과를 버림 sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("질문 생성 실패 sessionId={}", sessionId, e);
            generationTx.markFailed(sessionId);
        }
    }

    private GeneratedQuestions generateOrFallback(Session session, String jd, String docText) {
        try {
            List<String> texts = claudeClient.generateQuestions(
                    session.getCompanyType(), session.getStage(), session.getJobTitle(), jd, docText);
            return new GeneratedQuestions(texts, QuestionSourceType.CLAUDE);
        } catch (Exception e) {
            log.warn("Claude 질문 생성 실패, 시드 질문으로 대체 sessionId={}", session.getId(), e);
            List<String> seeds = seedQuestionRepository
                    .findTop5ByCompanyTypeAndStage(session.getCompanyType(), session.getStage())
                    .stream().map(SeedQuestion::getContent).toList();
            return new GeneratedQuestions(seeds, QuestionSourceType.SEED_FALLBACK);
        }
    }

    // 서류 텍스트 추출 (실패해도 null 반환, 질문 생성은 계속)
    private String extractDocumentText(Long docId) {
        if (docId == null) {
            return null;
        }
        try {
            Document document = documentRepository.findByIdAndDeletedAtIsNull(docId)
                    .orElse(null);
            if (document == null) {
                return null;
            }
            byte[] pdfBytes = s3StorageService.download(document.getStorageKey());
            return pdfTextExtractor.extract(pdfBytes);
        } catch (Exception e) {
            log.warn("서류 텍스트 추출 실패, 서류 없이 질문 생성. docId={}", docId, e);
            return null;
        }
    }

    private record GeneratedQuestions(List<String> texts, QuestionSourceType sourceType) {}
}
