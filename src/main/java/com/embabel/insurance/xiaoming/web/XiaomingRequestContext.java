package com.embabel.insurance.xiaoming.web;

import jakarta.servlet.http.HttpServletRequest;

public final class XiaomingRequestContext {

    public static final String TRACE_ID_ATTRIBUTE = XiaomingRequestContext.class.getName() + ".traceId";
    public static final String REQUEST_ID_ATTRIBUTE = XiaomingRequestContext.class.getName() + ".requestId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private XiaomingRequestContext() {
    }

    public static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(TRACE_ID_ATTRIBUTE));
    }

    public static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(REQUEST_ID_ATTRIBUTE));
    }
}
