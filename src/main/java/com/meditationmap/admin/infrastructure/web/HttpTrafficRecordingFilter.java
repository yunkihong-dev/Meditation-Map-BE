package com.meditationmap.admin.infrastructure.web;

import com.meditationmap.admin.application.HttpTrafficRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class HttpTrafficRecordingFilter extends OncePerRequestFilter {

    private final HttpTrafficRecorder trafficRecorder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldRecord(request)) {
                trafficRecorder.recordRequest(request.getRequestURI());
            }
        }
    }

    private static boolean shouldRecord(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && !"POST".equalsIgnoreCase(request.getMethod())
                && !"PUT".equalsIgnoreCase(request.getMethod())
                && !"PATCH".equalsIgnoreCase(request.getMethod())
                && !"DELETE".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        return !uri.startsWith("/actuator")
                && !uri.startsWith("/swagger-ui")
                && !uri.startsWith("/v3/api-docs");
    }
}
