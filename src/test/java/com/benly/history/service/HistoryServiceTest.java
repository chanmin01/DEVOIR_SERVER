package com.benly.history.service;

import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.entity.SessionFeedback;
import com.benly.global.exception.BusinessException;
import com.benly.history.dto.SessionHistoryResponse;
import com.benly.history.exception.HistoryErrorCode;
import com.benly.history.repository.SessionHistoryRepository;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.entity.Session;
import com.benly.session.entity.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class HistoryServiceTest {

    @Mock
    private SessionHistoryRepository sessionHistoryRepository;

    @Mock
    private PtSessionRepository ptSessionRepository;

    @InjectMocks
    private HistoryService historyService;

    private static final FeedbackStatus SCORED = FeedbackStatus.COMPLETED;

    @Test
    @DisplayName("필터 없이 조회하면 전체 완료 세션과 통계를 반환한다")
    void getMySessionWithoutFilter() {
        // given
        given(sessionHistoryRepository.findScoredSessions(1L, SCORED))
                .willReturn(List.of());
        given(sessionHistoryRepository.countScored(1L, SCORED))
                .willReturn(12L);
        given(sessionHistoryRepository
                .countScoredSince(eq(1L), eq(SCORED), any(LocalDateTime.class)))
                .willReturn(3L);

        // when
        SessionHistoryResponse response = historyService.getMySessions(1L, null);

        // then
        assertThat(response.totalCount()).isEqualTo(12L);
        assertThat(response.weekCount()).isEqualTo(3L);
        verify(sessionHistoryRepository, never())
                .findScoredSessionsByCompany(any(), any(), any());
    }

    @Test
    @DisplayName("유효한 기업유형 필터로 조회하면 필터 조회 메서드를 호출한다")
    void getMySessionsWithValidFilter() {
        // given
        given(sessionHistoryRepository.findScoredSessionsByCompany(1L, "SERVICE", SCORED))
                .willReturn(List.of());
        given(sessionHistoryRepository.countScored(1L, SCORED))
                .willReturn(5L);
        given(sessionHistoryRepository
                .countScoredSince(eq(1L), eq(SCORED), any(LocalDateTime.class)))
                .willReturn(1L);

        // when
        SessionHistoryResponse response = historyService.getMySessions(1L, "SERVICE");

        // then
        assertThat(response.totalCount()).isEqualTo(5L);
        verify(sessionHistoryRepository)
                .findScoredSessionsByCompany(1L, "SERVICE", SCORED);
    }

    @Test
    @DisplayName("잘못된 기업유형 필터는 400 예외를 던지고 조회하지 않는다")
    void getMySessionsWithInvalidFilter() {
        // when & then
        assertThatThrownBy(() -> historyService.getMySessions(1L, "INVALID_TYPE"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", HistoryErrorCode.INVALID_COMPANY_TYPE);

        verify(sessionHistoryRepository, never())
                .findScoredSessionsByCompany(any(), any(), any());
        verify(sessionHistoryRepository, never())
                .findScoredSessions(any(), any());
    }

    @Test
    @DisplayName("pt_sessions 행이 있는 세션은 PT, 없는 세션은 QA로 내려준다")
    void getMySessionsWithFormat() {
        // given
        SessionFeedback qa = scoredFeedback(1L);
        SessionFeedback pt = scoredFeedback(2L);
        given(sessionHistoryRepository.findScoredSessions(1L, SCORED))
                .willReturn(List.of(qa, pt));
        given(ptSessionRepository.findIdsByIdIn(List.of(1L, 2L)))
                .willReturn(Set.of(2L));

        // when
        SessionHistoryResponse response = historyService.getMySessions(1L, null);

        // then
        assertThat(response.sessions())
                .extracting(SessionHistoryResponse.Item::sessionId, SessionHistoryResponse.Item::format)
                .containsExactly(
                        tuple(1L, "QA"),
                        tuple(2L, "PT"));
    }

    @Test
    @DisplayName("조회된 세션이 없으면 PT 세션 조회를 하지 않는다")
    void skipPtLookupWhenEmpty() {
        // given
        given(sessionHistoryRepository.findScoredSessions(1L, SCORED))
                .willReturn(List.of());

        // when
        historyService.getMySessions(1L, null);

        // then
        verify(ptSessionRepository, never()).findIdsByIdIn(any());
    }

    private SessionFeedback scoredFeedback(Long sessionId) {
        Session session = mock(Session.class);
        given(session.getId()).willReturn(sessionId);
        given(session.getStatus()).willReturn(SessionStatus.COMPLETED);

        SessionFeedback feedback = mock(SessionFeedback.class);
        given(feedback.getSession()).willReturn(session);
        return feedback;
    }
}
