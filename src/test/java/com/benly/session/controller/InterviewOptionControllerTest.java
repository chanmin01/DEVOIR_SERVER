package com.benly.session.controller;

import com.benly.pt.repository.PtSessionRepository;
import com.benly.session.dto.InterviewOptionResponse;
import com.benly.session.service.InterviewOptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewOptionController.class)
public class InterviewOptionControllerTest {

    @MockitoBean
    private PtSessionRepository ptSessionRepository;


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewOptionService interviewOptionService;

    @Test
    @DisplayName("설정 선택지 조회 - 성공")
    void getOptions_success() throws Exception {
        //given
        InterviewOptionResponse fakeResponse = new InterviewOptionResponse(
                List.of(new InterviewOptionResponse.CompanyType("SERVICE", "서비스 기업", "네이버·카카오")),
                List.of(new InterviewOptionResponse.Stage("TECHNICAL", "1차 기술")),
                List.of("백엔드")
        );
        given(interviewOptionService.getOptions()).willReturn(fakeResponse);

        // when & then: GET 요청하면
        mockMvc.perform(get("/api/v1/interview-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("설정 선택지를 조회했습니다."))
                .andExpect(jsonPath("$.data.companyTypes[0].label").value("서비스 기업"))
                .andExpect(jsonPath("$.data.jobRoleChips[0]").value("백엔드"));
    }
}