package com.embabel.insurance.xiaoming.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class XiaomingRequestContextFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/xiaoming";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = safeRequestId(request.getHeader(XiaomingRequestContext.REQUEST_ID_HEADER));
        String traceId = UUID.randomUUID().toString();

        request.setAttribute(XiaomingRequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(XiaomingRequestContext.TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(XiaomingRequestContext.REQUEST_ID_HEADER, requestId);
        response.setHeader(XiaomingRequestContext.TRACE_ID_HEADER, traceId);

        try (MDC.MDCCloseable ignoredTrace = MDC.putCloseable("traceId", traceId);
             MDC.MDCCloseable ignoredRequest = MDC.putCloseable("requestId", requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String safeRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
