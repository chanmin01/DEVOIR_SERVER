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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionGenerationServiceTest {

    @Mock private ClaudeClient claudeClient;
    @Mock private SeedQuestionRepository seedQuestionRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private S3StorageService s3StorageService;
    @Mock private PdfTextExtractor pdfTextExtractor;
    @Mock private QuestionGenerationTx generationTx;

    @InjectMocks
    private QuestionGenerationService questionGenerationService;

    private static final Long SESSION_ID = 1L;
    private static final Long DOC_ID = 10L;
    private static final List<String> FIVE = List.of("질문1", "질문2", "질문3", "질문4", "질문5");

    private void started() {
        Session session = mock(Session.class);
        given(session.getId()).willReturn(SESSION_ID);
        given(session.getCompanyType()).willReturn("SERVICE");
        given(session.getStage()).willReturn("TECHNICAL");
        given(session.getJobTitle()).willReturn("백엔드");
        given(generationTx.markStarted(SESSION_ID)).willReturn(Optional.of(session));
    }

    private void seeds(int count) {
        SeedQuestion seed = mock(SeedQuestion.class);
        given(seed.getContent()).willReturn("시드 질문");
        given(seedQuestionRepository.findTop5ByCompanyTypeAndStage("SERVICE", "TECHNICAL"))
                .willReturn(java.util.Collections.nCopies(count, seed));
    }

    @Test
    @DisplayName("서류가 있으면 텍스트를 뽑아 Claude에 넘기고 질문을 저장한다")
    void generateWithDocument() {
        started();
        Document document = mock(Document.class);
        given(document.getStorageKey()).willReturn("documents/1/abc.pdf");
        given(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).willReturn(Optional.of(document));
        given(s3StorageService.download("documents/1/abc.pdf")).willReturn("pdf".getBytes());
        given(pdfTextExtractor.extract(any())).willReturn("자소서 내용");
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), eq("자소서 내용")))
                .willReturn(FIVE);
        given(generationTx.saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE)).willReturn(true);

        questionGenerationService.generate(SESSION_ID, "채용공고", DOC_ID);

        verify(claudeClient).generateQuestions(anyString(), anyString(), anyString(), any(), eq("자소서 내용"));
        verify(generationTx).saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE);
        verify(generationTx, never()).markFailed(any());
    }

    @Test
    @DisplayName("서류가 없으면 서류 조회 없이 질문을 만든다")
    void generateWithoutDocument() {
        started();
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), isNull()))
                .willReturn(FIVE);
        given(generationTx.saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE)).willReturn(true);

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(documentRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(s3StorageService, never()).download(anyString());
        verify(generationTx).saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE);
    }

    @Test
    @DisplayName("서류 추출에 실패해도 서류 없이 질문을 만든다")
    void documentFailStillGenerates() {
        started();
        Document document = mock(Document.class);
        given(document.getStorageKey()).willReturn("documents/1/abc.pdf");
        given(documentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).willReturn(Optional.of(document));
        given(s3StorageService.download(anyString())).willThrow(new RuntimeException("S3 실패"));
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), isNull()))
                .willReturn(FIVE);
        given(generationTx.saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE)).willReturn(true);

        questionGenerationService.generate(SESSION_ID, "채용공고", DOC_ID);

        verify(claudeClient).generateQuestions(anyString(), anyString(), anyString(), any(), isNull());
        verify(generationTx).saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE);
    }

    @Test
    @DisplayName("Claude가 실패하면 시드 질문으로 대체한다")
    void claudeFailFallsBackToSeed() {
        started();
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), any()))
                .willThrow(new RuntimeException("Claude 오류"));
        seeds(5);
        given(generationTx.saveQuestions(eq(SESSION_ID), anyList(), eq(QuestionSourceType.SEED_FALLBACK)))
                .willReturn(true);

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(generationTx).saveQuestions(eq(SESSION_ID), anyList(), eq(QuestionSourceType.SEED_FALLBACK));
        verify(generationTx, never()).markFailed(any());
    }

    @Test
    @DisplayName("Claude가 빈 결과를 주면 시드 질문으로 대체한다")
    void claudeEmptyFallsBackToSeed() {
        started();
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), any()))
                .willReturn(List.of());
        seeds(5);
        given(generationTx.saveQuestions(eq(SESSION_ID), anyList(), eq(QuestionSourceType.SEED_FALLBACK)))
                .willReturn(true);

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(generationTx).saveQuestions(eq(SESSION_ID), anyList(), eq(QuestionSourceType.SEED_FALLBACK));
        verify(generationTx, never()).markFailed(any());
    }

    @Test
    @DisplayName("Claude와 시드 모두 비어 있으면 READY로 바꾸지 않고 실패 처리한다")
    void emptyResultMarksFailed() {
        started();
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), any()))
                .willReturn(List.of());
        seeds(0);

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(generationTx, never()).saveQuestions(any(), any(), any());
        verify(generationTx).markFailed(SESSION_ID);
    }

    @Test
    @DisplayName("저장 중 예외가 나면 실패 처리한다")
    void markFailedOnError() {
        started();
        given(claudeClient.generateQuestions(anyString(), anyString(), anyString(), any(), any()))
                .willReturn(FIVE);
        given(generationTx.saveQuestions(SESSION_ID, FIVE, QuestionSourceType.CLAUDE))
                .willThrow(new RuntimeException("DB 오류"));

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(generationTx).markFailed(SESSION_ID);
    }

    @Test
    @DisplayName("이미 정리된 세션이면 Claude를 부르지 않는다")
    void skipWhenAlreadyClosed() {
        given(generationTx.markStarted(SESSION_ID)).willReturn(Optional.empty());

        questionGenerationService.generate(SESSION_ID, "채용공고", null);

        verify(claudeClient, never()).generateQuestions(any(), any(), any(), any(), any());
        verify(generationTx, never()).saveQuestions(any(), any(), any());
        verify(generationTx, never()).markFailed(any());
    }
}
