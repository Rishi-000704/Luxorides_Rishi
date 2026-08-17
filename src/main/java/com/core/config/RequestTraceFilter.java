package com.core.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE =
            RequestTraceFilter.class.getName() + ".TRACE_ID";

    public static final String TRACE_ID_HEADER =
            "X-Trace-Id";

    private static final String TRACE_ID_MDC_KEY =
            "traceId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();

        request.setAttribute(
                TRACE_ID_ATTRIBUTE,
                traceId
        );

        response.setHeader(
                TRACE_ID_HEADER,
                traceId
        );

        MDC.put(
                TRACE_ID_MDC_KEY,
                traceId
        );

        try {
            filterChain.doFilter(
                    request,
                    response
            );
        } finally {
            long durationMillis =
                    (System.nanoTime() - startedAt)
                            / 1_000_000;

            log.debug(
                    "HTTP request completed method={} path={} status={} durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis
            );

            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}