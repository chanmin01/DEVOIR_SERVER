package com.benly.pt;

import com.benly.archive.entity.InterviewFormat;
import com.benly.auth.jwt.*;
import com.benly.global.config.*;
import com.benly.global.exception.BusinessException;
import com.benly.pt.controller.PtSessionController;
import com.benly.pt.dto.*;
import com.benly.pt.entity.PtPhase;
import com.benly.pt.exception.*;
import com.benly.pt.service.PtProgressService;
import com.benly.pt.service.PtSessionService;
import com.benly.session.entity.SessionStatus;
import com.benly.user.entity.UserType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.benly.pt.entity.PtNextAction;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(PtSessionControllerTest.Config.class)
@WebAppConfiguration
class PtSessionControllerTest {

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
        PtProgressService progressService() {
            return mock(PtProgressService.class);
        }

        @Bean
        PtSessionService service() {
            return mock(PtSessionService.class);
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    JwtProvider jwt;

    @Autowired
    PtSessionService service;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(jwt, service);

        when(jwt.isValid("valid")).thenReturn(true);
        when(jwt.getUserId("valid")).thenReturn(1L);
        when(jwt.getUserType("valid"))
                .thenReturn(UserType.MEMBER);

        mvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void serializesCreationResponseAndPassesPrincipal()
            throws Exception {
        Instant now =
                Instant.parse("2026-11-03T05:00:00Z");

        when(service.create(eq(1L), any()))
                .thenReturn(new PtSessionResponse(
                        100L,
                        InterviewFormat.PT,
                        SessionStatus.IN_PROGRESS,
                        PtPhase.PREPARING,
                        "백엔드",
                        null,
                        20,
                        now,
                        now.plusSeconds(1200),
                        now,
                        null,
                        null,
                        null,
                        null,
                        null,
                        PtNextAction.SELECT_TRACK
                ));

        mvc.perform(post("/api/v1/pt/sessions")
                        .header(
                                "Authorization",
                                "Bearer valid"
                        )
                        .contentType("application/json")
                        .content("""
                            {
                              "requestId": "6aeb070f-a26f-48e7-a00a-ff4d20517301",
                              "companyId": 7,
                              "preparationMinutes": 20,
                              "jobRole": "백엔드"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId")
                        .value(100))
                .andExpect(jsonPath("$.data.status")
                        .value("IN_PROGRESS"))
                .andExpect(jsonPath(
                        "$.data.preparationDeadline"
                ).value("2026-11-03T05:20:00Z"));
    }

    @Test
    void rejectsMissingAndMalformedRequest()
            throws Exception {
        String[] bodies = {
                "{}",
                """
                {
                  "requestId": "invalid",
                  "companyId": 7,
                  "preparationMinutes": 20
                }
                """
        };

        for (String body : bodies) {
            mvc.perform(post("/api/v1/pt/sessions")
                            .header(
                                    "Authorization",
                                    "Bearer valid"
                            )
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(400))
                    .andExpect(jsonPath("$.meta.path")
                            .value("/api/v1/pt/sessions"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void mapsOwnershipFailureToExistingErrorEnvelope()
            throws Exception {
        when(service.get(1L, 100L))
                .thenThrow(new BusinessException(
                        PtErrorCode.SESSION_NOT_FOUND
                ));

        mvc.perform(get("/api/v1/pt/sessions/100")
                        .header(
                                "Authorization",
                                "Bearer valid"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.meta.timestamp")
                        .isNumber());
    }

    @Test
    void rejectsInvalidPathParameter() throws Exception {
        mvc.perform(get("/api/v1/pt/sessions/abc")
                        .header(
                                "Authorization",
                                "Bearer valid"
                        ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}