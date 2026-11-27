package com.ticketlock.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiTraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/")) {
            String traceId = UUID.randomUUID().toString();
            request.setAttribute("ticketlock.traceId", traceId);
            response.setHeader("X-Trace-Id", traceId);
        }
        chain.doFilter(request, response);
    }
}
