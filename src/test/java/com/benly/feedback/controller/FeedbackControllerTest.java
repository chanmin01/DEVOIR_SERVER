
package com.benly.feedback.controller;

import com.benly.pt.repository.PtSessionRepository;
import com.benly.feedback.dto.FeedbackReportResponse;
import com.benly.feedback.dto.FeedbackScoringResponse;
import com.benly.feedback.dto.FeedbackStatusResponse;
import com.benly.feedback.entity.FeedbackStatus;
import com.benly.feedback.service.FeedbackQueryService;
import com.benly.feedback.service.FeedbackRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
class FeedbackControllerTest {

    @MockitoBean
    private PtSessionRepository ptSessionRepository;


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackRequestService feedbackRequestService;

    @MockitoBean
    private FeedbackQueryService feedbackQueryService;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 8L;

    // 인증된 요청으로 인식되게 SecurityContext에 principal을 심는다(401 방지)
    private RequestPostProcessor loginUser() {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Test
    @DisplayName("채점 생성 - 202 Accepted와 SCORING 상태를 반환하고 서비스를 호출한다")
    void createScoring() throws Exception {
        // given
        given(feedbackRequestService.requestScoring(any(), any()))
                .willReturn(FeedbackScoringResponse.from(SESSION_ID));

        // when & then
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/feedback", SESSION_ID).with(loginUser()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.sessionId").value(8))
                .andExpect(jsonPath("$.data.status").value("SCORING"));

        verify(feedbackRequestService).requestScoring(any(), any());
    }

    @Test
    @DisplayName("채점 상태 조회 - 현재 상태를 반환한다")
    void getStatus() throws Exception {
        // given
        given(feedbackQueryService.getStatus(any(), any()))
                .willReturn(FeedbackStatusResponse.of(SESSION_ID, FeedbackStatus.COMPLETED));

        // when & then
        mockMvc.perform(get("/api/v1/sessions/{sessionId}/feedback/status", SESSION_ID).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(8))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(feedbackQueryService).getStatus(any(), any());
    }

    @Test
    @DisplayName("리포트 조회 - 총점 등 리포트를 반환한다")
    void getReport() throws Exception {
        // given
        FeedbackReportResponse report = new FeedbackReportResponse(
                SESSION_ID, null, null, 83, "총평", null, List.of(), null);
        given(feedbackQueryService.getReport(any(), any()))
                .willReturn(report);

        // when & then
        mockMvc.perform(get("/api/v1/sessions/{sessionId}/feedback", SESSION_ID).with(loginUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(8))
                .andExpect(jsonPath("$.data.totalScore").value(83));

        verify(feedbackQueryService).getReport(any(), any());
    }
}