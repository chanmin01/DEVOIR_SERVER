package com.benly.pt;

import com.benly.auth.jwt.JwtAuthenticationEntryPoint;
import com.benly.auth.jwt.JwtProvider;
import com.benly.global.config.JacksonConfig;
import com.benly.global.config.JwtAccessDeniedHandler;
import com.benly.global.config.SecurityConfig;
import com.benly.pt.controller.PtOptionController;
import com.benly.pt.dto.PtCompanyOptionResponse;
import com.benly.pt.entity.PtTaskStatus;
import com.benly.pt.repository.PtTaskRepository;
import com.benly.pt.service.PtOptionService;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(PtOptionControllerTest.TestConfig.class)
@WebAppConfiguration
class PtOptionControllerTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
            PtOptionController.class,
            PtOptionService.class,
            SecurityConfig.class,
            JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class,
            JacksonConfig.class
    })
    static class TestConfig {

        @Bean
        PtTaskRepository ptTaskRepository() {
            var tasks = mock(PtTaskRepository.class);

            given(tasks.findAvailableCompanies(
                    PtTaskStatus.APPROVED
            )).willReturn(List.of(
                    new PtCompanyOptionResponse(
                            7L,
                            "기업은행"
                    )
            ));

            return tasks;
        }

        @Bean
        JwtProvider jwtProvider() {
            return mock(JwtProvider.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(jwtProvider);

        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    @DisplayName("회원과 게스트는 PT 설정을 조회한다")
    void getOptionsWithToken(UserType userType)
            throws Exception {
        given(jwtProvider.isValid("valid-token"))
                .willReturn(true);

        given(jwtProvider.getUserId("valid-token"))
                .willReturn(1L);

        given(jwtProvider.getUserType("valid-token"))
                .willReturn(userType);

        mockMvc.perform(get("/api/v1/pt/options")
                        .header(
                                "Authorization",
                                "Bearer valid-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message")
                        .value("PT 설정을 조회했습니다."))
                .andExpect(jsonPath(
                        "$.data.tracks",
                        hasSize(4)
                ))
                .andExpect(jsonPath(
                        "$.data.tracks[*].code",
                        contains(
                                "SECURITY",
                                "UI_UX",
                                "AI",
                                "DATA"
                        )
                ))
                .andExpect(jsonPath(
                        "$.data.tracks[*].label",
                        contains(
                                "보안",
                                "UI/UX",
                                "AI",
                                "데이터"
                        )
                ))
                .andExpect(jsonPath(
                        "$.data.preparationMinutes",
                        contains(15, 20)
                ))
                .andExpect(jsonPath(
                        "$.data.presentationSeconds"
                ).value(300))
                .andExpect(jsonPath(
                        "$.data.mainQuestionCount"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.followUpQuestionCountPerMain"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.totalQuestionCount"
                ).value(6));
    }

    @Test
    void getOptionsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/pt/options"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message")
                        .value("인증이 필요합니다."))
                .andExpect(jsonPath("$.meta.path")
                        .value("/api/v1/pt/options"))
                .andExpect(jsonPath("$.meta.timestamp")
                        .isNumber());
    }

    @Test
    void getOptionsWithInvalidToken() throws Exception {
        given(jwtProvider.isValid("invalid-token"))
                .willReturn(false);

        mockMvc.perform(get("/api/v1/pt/options")
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}