package com.benly.pt;

import com.benly.global.exception.BusinessException;
import com.benly.pt.config.PtLegacyApiGuard;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.PtSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PtLegacyApiGuardTest {
    private final PtSessionRepository details = mock(PtSessionRepository.class);
    private final PtLegacyApiGuard guard = new PtLegacyApiGuard(details);

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private boolean allowed(MockHttpServletRequest request) {
        return guard.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void ownedPtSkipIsBlockedUsingQueryParameter() {
        var request = new MockHttpServletRequest("POST", "/api/v1/questions/201/skip");
        request.setParameter("sessionId", "101");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("questionId", "201"));
        when(details.findOwnerId(101L)).thenReturn(Optional.of(1L));
        assertEquals(PtErrorCode.PT_ROUTE_REQUIRED,
                assertThrows(BusinessException.class, () -> allowed(request)).getErrorCode());
    }

    @Test
    void anotherOwnersPtIsLeftToExistingServiceAuthorization() {
        var request = new MockHttpServletRequest("POST", "/api/v1/questions/201/skip");
        request.setParameter("sessionId", "101");
        when(details.findOwnerId(101L)).thenReturn(Optional.of(2L));
        assertTrue(allowed(request));
    }

    @Test
    void ordinaryOrMissingSessionKeepsExistingServiceBehavior() {
        var request = new MockHttpServletRequest("POST", "/api/v1/questions/201/skip");
        request.setParameter("sessionId", "102");
        when(details.findOwnerId(102L)).thenReturn(Optional.empty());
        assertTrue(allowed(request));
    }

    @Test
    void existingOwnedPtSessionRouteIsStillBlocked() {
        var request = new MockHttpServletRequest("GET", "/api/v1/sessions/101/current-question");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("sessionId", "101"));
        when(details.findOwnerId(101L)).thenReturn(Optional.of(1L));
        assertThrows(BusinessException.class, () -> allowed(request));
    }

    @ParameterizedTest
    @CsvSource({"GET,feedback", "HEAD,feedback", "GET,feedback/status", "HEAD,feedback/status"})
    void feedbackReadsWithContextPathRemainAllowed(String method, String suffix) {
        var request = new MockHttpServletRequest(method,
                "/devoir/api/v1/sessions/101/" + suffix);
        request.setContextPath("/devoir");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("sessionId", "101"));
        assertTrue(allowed(request));
        verifyNoInteractions(details);
    }

    @Test
    void invalidSessionParameterIsLeftToControllerBinding() {
        var request = new MockHttpServletRequest("POST", "/api/v1/questions/201/skip");
        request.setParameter("sessionId", "invalid");
        assertTrue(allowed(request));
        verifyNoInteractions(details);
    }
}
