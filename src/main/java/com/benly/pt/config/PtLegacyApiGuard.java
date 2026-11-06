package com.benly.pt.config;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.PtSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PtLegacyApiGuard implements HandlerInterceptor {

    private final PtSessionRepository details;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        boolean read = "GET".equals(request.getMethod())
                || "HEAD".equals(request.getMethod());

        if (read && path.matches(
                "/api/v1/sessions/[^/]+/feedback(?:/status)?"
        )) {
            return true;
        }

        String value = null;

        Object variables = request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );

        if (variables instanceof Map<?, ?> map
                && map.get("sessionId") != null) {
            value = map.get("sessionId").toString();
        } else if (path.matches(
                "/api/v1/questions/[^/]+/skip"
        )) {
            // 일반 skip API는 sessionId를 쿼리 파라미터로 받는다.
            value = request.getParameter("sessionId");
        }

        if (value == null) {
            return true;
        }

        Long sessionId;

        try {
            sessionId = Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            // 형식 오류는 기존 컨트롤러 바인딩에서 처리한다.
            return true;
        }

        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof Long userId)) {
            return true;
        }

        // 타인의 세션은 기존 서비스의 권한 검사에 맡긴다.
        if (details.findOwnerId(sessionId)
                .filter(userId::equals)
                .isPresent()) {
            throw new BusinessException(
                    PtErrorCode.PT_ROUTE_REQUIRED
            );
        }

        return true;
    }
}