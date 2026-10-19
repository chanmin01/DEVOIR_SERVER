package com.benly.auth.jwt;

import com.benly.user.entity.UserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/v1/auth/kakao/login",
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/guest"      // 만료된 게스트 토큰을 들고 재발급하러 오므로 제외
    );

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // JWT Bearer 검증 로직만 try 블록에 위치시킴
            String token = resolveToken(request);

            if (token != null && jwtProvider.isValid(token)) {
                Long userId = jwtProvider.getUserId(token);
                UserType userType = jwtProvider.getUserType(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                // ROLE_GUEST / ROLE_MEMBER → SecurityConfig의 hasRole로 분기
                                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + userType.name()))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException e) {
            // 액세스 토큰 만료 시 에러를 응답하고 return을 호출하여 필터 체인을 즉시 중단
            setErrorResponse(response, "액세스 토큰이 만료되었습니다. (EXPIRED_ACCESS_TOKEN)");
            return;
        }

        // try-catch 블록 밖에서 호출하여, 후속 컨트롤러/서비스에서 발생한 예외를 필터가 가로채지 않도록 함
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private void setErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "code", 4011,
                "message", message
        );

        objectMapper.writeValue(response.getWriter(), body);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}