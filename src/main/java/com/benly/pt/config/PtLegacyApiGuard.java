package com.benly.pt.config;

import com.benly.global.exception.BusinessException;
import com.benly.pt.exception.PtErrorCode;
import com.benly.pt.repository.PtSessionRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

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
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 평가 담당자가 기존 결과 조회 API를 재사용할 수 있도록 읽기는 유지한다.
        if ("GET".equals(request.getMethod())
                && request.getRequestURI().matches("/api/v1/sessions/[^/]+/feedback(?:/status)?")) {
            return true;
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map && map.get("sessionId") != null) {
            try {
                Long sessionId = Long.valueOf(map.get("sessionId").toString());
                if (details.existsById(sessionId))
                    throw new BusinessException(PtErrorCode.PT_ROUTE_REQUIRED);
            } catch (NumberFormatException ignored) {
                // 실제 매개변수 바인딩에서 기존 400 처리.
            }
        }
        return true;
    }
}