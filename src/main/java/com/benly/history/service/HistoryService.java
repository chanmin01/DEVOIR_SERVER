package com.benly.history.service;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.global.exception.BusinessException;
import com.benly.history.dto.SessionHistoryResponse;
import com.benly.history.exception.HistoryErrorCode;
import com.benly.history.repository.SessionHistoryRepository;
import com.benly.session.entity.CompanyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private static final FeedbackStatus SCORED = FeedbackStatus.COMPLETED;

    private final SessionHistoryRepository sessionHistoryRepository;

    public SessionHistoryResponse getMySessions(Long userId, String companyType) {
        List<SessionFeedback> feedbacks = findScoredSessions(userId, companyType);

        long totalCount = sessionHistoryRepository.countScored(userId, SCORED);
        long weekCount = sessionHistoryRepository.countScoredSince(userId, SCORED, thisWeekStart());

        return SessionHistoryResponse.of(totalCount, weekCount, feedbacks);
    }

    private List<SessionFeedback> findScoredSessions(Long userId, String companyType) {
        if (companyType == null || companyType.isBlank()) {
            return sessionHistoryRepository.findScoredSessions(userId, SCORED);
        }
        String validated = validateCompanyType(companyType);
        return sessionHistoryRepository.findScoredSessionsByCompany(userId, validated, SCORED);
    }

    private String validateCompanyType(String companyType) {
        try {
            return CompanyType.valueOf(companyType).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HistoryErrorCode.INVALID_COMPANY_TYPE);
        }
    }

    private LocalDateTime thisWeekStart() {
        return LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
    }
}