package com.benly.session.controller;


import com.benly.pt.repository.PtSessionRepository;
import com.benly.question.service.QuestionGenerationService;
import com.benly.session.dto.SessionCreateRequest;
import com.benly.session.dto.SessionCreateResponse;
import com.benly.session.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
public class SessionControllerTest {

    @MockitoBean
    private PtSessionRepository ptSessionRepository;


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private QuestionGenerationService questionGenerationService;

    @Test
    @DisplayName("세션 생성 - 성공")
    void createSession_success() throws Exception {
        // given: 서비스가 이런 응답을 준다고 가정
        SessionCreateResponse fakeResponse = SessionCreateResponse.from(101L, "GENERATING");
        given(sessionService.createSession(anyLong(), any(SessionCreateRequest.class)))
                .willReturn(fakeResponse);

        // 요청 body 만들기
        SessionCreateRequest request = new SessionCreateRequest(
                "SERVICE", "TECHNICAL", "카카오", "백엔드", "JD내용", 11L
        );
        String requestJson = objectMapper.writeValueAsString(request);

        // when & then: POST 요청
        mockMvc.perform(post("/api/v1/sessions")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value(101))
                .andExpect(jsonPath("$.data.status").value("GENERATING"))
                .andExpect(jsonPath("$.data.generationStatusUrl")
                        .value("/api/v1/sessions/101/generation-status"));
    }

    @Test
    @DisplayName("세션 생성 - 필수값 누락 시 400")
    void createSession_missingRequired() throws Exception {
        // given: companyType 없는 잘못된 요청
        String invalidJson = """
                {
                    "interviewStage": "TECHNICAL"
                }
                """;

        // when & then: 검증 실패로 400
        mockMvc.perform(post("/api/v1/sessions")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());   // 400
    }
}