package com.benly.history.service;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.global.exception.BusinessException;
import com.benly.history.dto.SessionHistoryResponse;
import com.benly.history.exception.HistoryErrorCode;
import com.benly.history.repository.SessionHistoryRepository;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.entity.CompanyType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private static final FeedbackStatus SCORED = FeedbackStatus.COMPLETED;

    private final SessionHistoryRepository sessionHistoryRepository;
    private final PtSessionRepository ptSessionRepository;

    public SessionHistoryResponse getMySessions(Long userId, String companyType) {
        List<SessionFeedback> feedbacks = findScoredSessions(userId, companyType);

        long totalCount = sessionHistoryRepository.countScored(userId, SCORED);
        long weekCount = sessionHistoryRepository.countScoredSince(userId, SCORED, thisWeekStart());

        return SessionHistoryResponse.of(totalCount, weekCount, feedbacks, findPtSessionIds(feedbacks));
    }

    private List<SessionFeedback> findScoredSessions(Long userId, String companyType) {
        if (companyType == null || companyType.isBlank()) {
            return sessionHistoryRepository.findScoredSessions(userId, SCORED);
        }
        String validated = validateCompanyType(companyType);
        return sessionHistoryRepository.findScoredSessionsByCompany(userId, validated, SCORED);
    }

    // 목록에 있는 세션 중 pt_sessions 행이 있는 ID만 모은다 (IN 쿼리 1번)
    private Set<Long> findPtSessionIds(List<SessionFeedback> feedbacks) {
        if (feedbacks.isEmpty()) {
            return Set.of();   // 빈 IN () 쿼리를 보내지 않는다
        }
        List<Long> ids = feedbacks.stream()
                .map(sf -> sf.getSession().getId())
                .toList();
        return ptSessionRepository.findIdsByIdIn(ids);
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
