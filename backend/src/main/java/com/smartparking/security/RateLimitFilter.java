package com.smartparking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP sliding-window rate limiting: strict on /api/auth/**, generous on
 * the rest. Websocket upgrades and health probes are excluded.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;

    @Value("${app.rate-limit.auth-per-minute}")
    private int authLimit;

    @Value("${app.rate-limit.api-per-minute}")
    private int apiLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/ws")
                || path.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        boolean allowed = path.startsWith("/api/auth/")
                ? rateLimiter.tryAcquire("auth:" + ip, authLimit)
                : rateLimiter.tryAcquire("api:" + ip, apiLimit);

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too many requests. Please slow down.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
