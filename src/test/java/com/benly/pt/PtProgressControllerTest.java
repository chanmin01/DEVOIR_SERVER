package com.benly.pt;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.benly.auth.jwt.*;
import com.benly.global.config.*;
import com.benly.global.exception.BusinessException;
import com.benly.pt.controller.PtSessionController;
import com.benly.pt.dto.*;
import com.benly.pt.entity.*;
import com.benly.pt.exception.*;
import com.benly.pt.service.*;
import com.benly.question.entity.AnswerType;
import com.benly.session.entity.SessionStatus;
import com.benly.user.entity.UserType;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.UUID;

@SpringJUnitConfig(PtProgressControllerTest.Config.class)
@WebAppConfiguration
class PtProgressControllerTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            PtSessionController.class,
            PtExceptionHandler.class,
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            JacksonConfig.class
    })
    static class Config {
        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }

        @Bean
        PtSessionService service() {
            return mock(PtSessionService.class);
        }

        @Bean
        PtProgressService progress() {
            return mock(PtProgressService.class);
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired JwtProvider jwt;
    @Autowired PtProgressService progress;
    MockMvc mvc;
    private static final Instant NOW = Instant.parse("2026-11-04T04:00:00Z");

    @BeforeEach
    void setup() {
        reset(jwt, progress);
        when(jwt.isValid("valid")).thenReturn(true);
        when(jwt.getUserId("valid")).thenReturn(1L);
        when(jwt.getUserType("valid")).thenReturn(UserType.MEMBER);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    PtProgressResponse view(PtPhase phase) {
        return new PtProgressResponse(
                100L,
                phase == PtPhase.CANCELED
                        ? SessionStatus.CANCELED
                        : SessionStatus.IN_PROGRESS,
                phase,
                PtTrack.DATA,
                NOW.plusSeconds(1200),
                NOW,
                NOW.plusSeconds(300),
                NOW,
                phase == PtPhase.GENERATING
                        ? new PtPresentationResponse(
                        "발표",
                        AnswerType.TEXT,
                        NOW
                )
                        : null,
                switch (phase) {
                    case PREPARING ->
                            PtNextAction.START_PRESENTATION;
                    case PRESENTING ->
                            PtNextAction.SUBMIT_PRESENTATION;
                    case GENERATING ->
                            PtNextAction.GENERATE_QUESTION;
                    case CANCELED ->
                            PtNextAction.NONE;
                    default ->
                            PtNextAction.FINISH;
                }
        );
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void membersAndGuestsSelectTrack(UserType role) throws Exception {
        when(jwt.getUserType("valid")).thenReturn(role);
        when(progress.selectTrack(eq(1L), eq(100L), any())).thenReturn(view(PtPhase.PREPARING));
        mvc.perform(
                        put("/api/v1/pt/sessions/100/track")
                                .header("Authorization", "Bearer valid")
                                .contentType("application/json")
                                .content("{\"track\":\"DATA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.track").value("DATA"))
                .andExpect(jsonPath("$.data.nextAction").value("START_PRESENTATION"));
        verify(progress).selectTrack(1L, 100L, new PtTrackRequest(PtTrack.DATA));
    }

    @Test
    void rejectsMissingAndUnknownTrack() throws Exception {
        for (String json : new String[] {"{}", "{\"track\":\"DEVOPS\"}"})
            mvc.perform(
                            put("/api/v1/pt/sessions/100/track")
                                    .header("Authorization", "Bearer valid")
                                    .contentType("application/json")
                                    .content(json))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(progress);
    }

    @Test
    void startsPresentationAndSerializesDeadline() throws Exception {
        when(progress.startPresentation(1L, 100L)).thenReturn(view(PtPhase.PRESENTING));
        mvc.perform(
                        post("/api/v1/pt/sessions/100/presentation/start")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presentationDeadline").value("2026-11-04T04:05:00Z"));
    }

    @Test
    void submitsTextAndPassesRequestId() throws Exception {
        UUID request = UUID.fromString("6aeb070f-a26f-48e7-a00a-ff4d20517301");
        when(progress.submitText(eq(1L), eq(100L), any())).thenReturn(view(PtPhase.GENERATING));
        mvc.perform(
                        post("/api/v1/pt/sessions/100/presentation/text")
                                .header("Authorization", "Bearer valid")
                                .contentType("application/json")
                                .content(
                                        "{\"requestId\":\""
                                                + request
                                                + "\",\"transcript\":\"발표\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("GENERATING"))
                .andExpect(jsonPath("$.data.presentation.inputType").value("TEXT"));
        verify(progress).submitText(1L, 100L, new PtTextPresentationRequest(request, "발표"));
    }

    @Test
    void validatesTextBeforeServiceCall() throws Exception {
        String uuid = "6aeb070f-a26f-48e7-a00a-ff4d20517301";
        for (String json :
                new String[] {
                        "{}",
                        "{\"requestId\":\"bad\",\"transcript\":\"내용\"}",
                        "{\"requestId\":\"" + uuid + "\",\"transcript\":\"  \"}",
                        "{\"requestId\":\"" + uuid + "\",\"transcript\":\"" + "가".repeat(30001) + "\"}"
                })
            mvc.perform(
                            post("/api/v1/pt/sessions/100/presentation/text")
                                    .header("Authorization", "Bearer valid")
                                    .contentType("application/json")
                                    .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(progress);
    }

    @Test
    void cancelsViaAuthenticatedPrincipal() throws Exception {
        when(progress.cancel(1L, 100L)).thenReturn(view(PtPhase.CANCELED));
        mvc.perform(post("/api/v1/pt/sessions/100/cancel").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void unauthenticatedMutationIsRejected() throws Exception {
        mvc.perform(post("/api/v1/pt/sessions/100/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(progress);
    }

    @Test
    void stateConflictUsesExistingEnvelope() throws Exception {
        when(progress.startPresentation(1L, 100L))
                .thenThrow(new BusinessException(PtErrorCode.TRACK_REQUIRED));
        mvc.perform(
                        post("/api/v1/pt/sessions/100/presentation/start")
                                .header("Authorization", "Bearer valid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(
                        jsonPath("$.meta.path")
                                .value("/api/v1/pt/sessions/100/presentation/start"));
    }

    @Test
    void ownershipFailureIsNotFound() throws Exception {
        when(progress.cancel(1L, 100L))
                .thenThrow(new BusinessException(PtErrorCode.SESSION_NOT_FOUND));
        mvc.perform(post("/api/v1/pt/sessions/100/cancel").header("Authorization", "Bearer valid"))
                .andExpect(status().isNotFound());
    }
}
