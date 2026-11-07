package com.benly.pt;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.benly.auth.jwt.*;
import com.benly.global.config.*;
import com.benly.global.exception.GlobalExceptionHandler;
import com.benly.pt.config.*;
import com.benly.pt.controller.PtInterviewController;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.PtExceptionHandler;
import com.benly.pt.repository.PtSessionRepository;
import com.benly.pt.service.*;
import com.benly.question.entity.AnswerType;
import com.benly.session.entity.SessionStatus;
import com.benly.user.entity.UserType;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.UUID;

@SpringJUnitConfig(PtCombinedControllerTest.Config.class)
@WebAppConfiguration
class PtCombinedControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            PtInterviewController.class,
            PtExceptionHandler.class,
            GlobalExceptionHandler.class,
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            JacksonConfig.class,
            PtLegacyApiGuard.class,
            PtWebConfig.class,
            LegacyStub.class
    })
    static class Config {
        @Bean
        JwtProvider jwt() {
            return mock(JwtProvider.class);
        }

        @Bean
        PtInterviewService interview() {
            return mock(PtInterviewService.class);
        }

        @Bean
        PtInterviewTx tx() {
            return mock(PtInterviewTx.class);
        }

        @Bean
        PtAudioService audio() {
            return mock(PtAudioService.class);
        }

        @Bean
        PtSessionRepository details() {
            return mock(PtSessionRepository.class);
        }
    }

    @RestController
    static class LegacyStub {
        @PostMapping("/api/v1/questions/{questionId}/skip")
        String skip(@PathVariable Long questionId, @RequestParam Long sessionId) {
            return "skip";
        }

        @PostMapping("/api/v1/sessions/{sessionId}/answers/text")
        String answer(@PathVariable Long sessionId) {
            return "normal";
        }

        @GetMapping("/api/v1/sessions/{sessionId}/feedback")
        String report(@PathVariable Long sessionId) {
            return "report";
        }

        @GetMapping("/api/v1/sessions/{sessionId}/feedback/status")
        String reportStatus(@PathVariable Long sessionId) {
            return "report-status";
        }

        @PostMapping("/api/v1/sessions/{sessionId}/feedback")
        String score(@PathVariable Long sessionId) {
            return "normal";
        }

        @PostMapping("/api/v1/sessions/{sessionId}/cancel")
        String cancel(@PathVariable Long sessionId) {
            return "canceled";
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @Autowired PtInterviewService interview;
    @Autowired PtInterviewTx tx;
    @Autowired PtAudioService audio;
    @Autowired PtSessionRepository details;
    MockMvc mvc;
    static final String PREFIX = "/api/v1/pt/sessions/101";
    static final UUID REQUEST = UUID.fromString("c03e307c-d9b7-4b39-8f10-b07ed184715d");

    @BeforeEach
    void setup() {
        reset(jwt, interview, tx, audio, details);
        when(jwt.isValid("valid")).thenReturn(true);
        when(jwt.getUserId("valid")).thenReturn(1L);
        when(jwt.getUserType("valid")).thenReturn(UserType.MEMBER);
        when(details.findOwnerId(101L)).thenReturn(java.util.Optional.of(1L));
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    PtInterviewProgressResponse state() {
        return new PtInterviewProgressResponse(
                101L,
                SessionStatus.IN_PROGRESS,
                PtPhase.QUESTIONING,
                0,
                6,
                false,
                new PtQuestionResponse(201L, 1, 1, 0, "MAIN", "문제 선정 이유는 무엇인가요?"),
                PtNextAction.ANSWER);
    }

    @Test
    void nextSupportsGuestAndReturnsQuestion() throws Exception {
        when(jwt.getUserType("valid")).thenReturn(UserType.GUEST);
        when(interview.next(1L, 101L)).thenReturn(state());
        mvc.perform(post(PREFIX + "/questions/next").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question.ordinal").value(1))
                .andExpect(jsonPath("$.data.nextAction").value("ANSWER"));
    }

    @Test
    void currentDoesNotGenerateQuestions() throws Exception {
        when(tx.current(1L, 101L)).thenReturn(state());
        mvc.perform(get(PREFIX + "/questions/current").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestionCount").value(6));
        verifyNoInteractions(interview);
    }

    @Test
    void textPassesPrincipalAndIdempotencyRequest() throws Exception {
        when(tx.submitText(eq(1L), eq(101L), eq(201L), any()))
                .thenReturn(new PtAnswerResponse(301L, 201L, AnswerType.TEXT, "답변", null, state()));
        mvc.perform(
                        post(PREFIX + "/questions/201/answers")
                                .header("Authorization", "Bearer valid")
                                .contentType("application/json")
                                .content(
                                        "{\"requestId\":\""
                                                + REQUEST
                                                + "\",\"transcript\":\"답변\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerId").value(301));
        verify(tx).submitText(1L, 101L, 201L, new PtAnswerRequest(REQUEST, "답변"));
    }

    @Test
    void invalidTextNeverReachesService() throws Exception {
        for (String body :
                new String[] {
                        "{}",
                        "{\"requestId\":\"bad\",\"transcript\":\"답변\"}",
                        "{\"requestId\":\"" + REQUEST + "\",\"transcript\":\" \"}"
                }) {
            mvc.perform(
                            post(PREFIX + "/questions/201/answers")
                                    .header("Authorization", "Bearer valid")
                                    .contentType("application/json")
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(tx);
    }

    @Test
    void audioAnswerBindsMultipart() throws Exception {
        when(audio.answer(eq(1L), eq(101L), eq(201L), eq(REQUEST), any(), eq(30)))
                .thenReturn(new PtAnswerResponse(301L, 201L, AnswerType.AUDIO, "전사", 30, state()));
        mvc.perform(
                        multipart(PREFIX + "/questions/201/answers/audio")
                                .file(
                                        new MockMultipartFile(
                                                "audio", "a.webm", "audio/webm", new byte[] {1, 2}))
                                .param("requestId", REQUEST.toString())
                                .param("durationSec", "30")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputType").value("AUDIO"));
    }

    @Test
    void missingMultipartFileAndRequestAre400() throws Exception {
        mvc.perform(
                        multipart(PREFIX + "/presentation/audio")
                                .param("requestId", REQUEST.toString())
                                .param("durationSec", "30")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.meta.path").value(PREFIX + "/presentation/audio"));
        verifyNoInteractions(audio);
    }

    @Test
    void audioPresentationBindsSeparateRoute() throws Exception {
        mvc.perform(
                        multipart(PREFIX + "/presentation/audio")
                                .file(
                                        new MockMultipartFile(
                                                "audio", "a.webm", "audio/webm", new byte[] {1, 2}))
                                .param("requestId", REQUEST.toString())
                                .param("durationSec", "30")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(audio).presentation(eq(1L), eq(101L), eq(REQUEST), any(), eq(30));
    }

    @Test
    void evaluationRouteIsReadOnlyAndAuthenticated() throws Exception {
        mvc.perform(get(PREFIX + "/evaluation-input").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(tx).evaluationInput(1L, 101L);
        mvc.perform(get(PREFIX + "/evaluation-input")).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedPathReturnsPt400() throws Exception {
        mvc.perform(
                        get("/api/v1/pt/sessions/abc/questions/current")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tx);
    }

    @Test
    void legacyAnswerIsBlockedOnlyForPt() throws Exception {
        mvc.perform(post("/api/v1/sessions/101/answers/text").header("Authorization", "Bearer valid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PT 전용 진행 API를 사용해주세요."));

        mvc.perform(
                        post("/api/v1/sessions/102/answers/text")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("normal"));
    }

    @Test
    void scoringRequestIsSharedWithPt() throws Exception {
        // 채점 API는 QA와 PT가 같이 쓴다
        mvc.perform(post("/api/v1/sessions/101/feedback").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("normal"));
    }

    @Test
    void existingReportReadsRemainAvailableForEvaluationBackend() throws Exception {
        mvc.perform(get("/api/v1/sessions/101/feedback").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("report"));
        mvc.perform(
                        get("/api/v1/sessions/101/feedback/status")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("report-status"));
    }

    @Test
    void commonCancelRemainsAvailableForPt() throws Exception {
        mvc.perform(post("/api/v1/sessions/101/cancel").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("canceled"));
    }
    @Test
    void wrongTextContentTypeUsesGeneral415Message() throws Exception {
        mvc.perform(post(PREFIX + "/questions/201/answers")
                        .header("Authorization", "Bearer valid")
                        .contentType("text/plain").content("invalid content type"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("요청 Content-Type을 확인해주세요."));
    }

    @Test
    void legacySkipWithQuerySessionIdIsBlockedForPt() throws Exception {
        mvc.perform(post("/api/v1/questions/201/skip")
                        .param("sessionId", "101")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PT 전용 진행 API를 사용해주세요."));
        mvc.perform(post("/api/v1/questions/202/skip")
                        .param("sessionId", "102")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(content().string("skip"));
    }
}