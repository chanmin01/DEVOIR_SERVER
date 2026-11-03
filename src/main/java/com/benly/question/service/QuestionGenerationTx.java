package com.benly.question.service;

import com.benly.question.entity.Question;
import com.benly.question.entity.QuestionSourceType;
import com.benly.question.repository.QuestionRepository;
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import com.benly.session.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuestionGenerationTx {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public Optional<Session> markStarted(Long sessionId) {
        int updated = sessionRepository.markGenerationStarted(sessionId, LocalDateTime.now());
        if (updated == 0) {
            return Optional.empty();
        }
        return sessionRepository.findById(sessionId);
    }

    @Transactional
    public boolean saveQuestions(Long sessionId, List<String> questionTexts, QuestionSourceType sourceType) {
        int updated = sessionRepository.updateStatusIfCurrent(
                sessionId, SessionStatus.GENERATING, SessionStatus.READY);
        if (updated == 0) {
            return false;
        }
        Session session = sessionRepository.getReferenceById(sessionId);
        int seq = 1;
        for (String text : questionTexts) {
            questionRepository.save(Question.createMain(session, seq++, text, sourceType));
        }
        return true;
    }

    @Transactional
    public void markFailed(Long sessionId) {
        sessionRepository.updateStatusIfCurrent(sessionId, SessionStatus.GENERATING, SessionStatus.FAILED);
    }
}
