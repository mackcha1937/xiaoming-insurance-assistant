package com.embabel.insurance.xiaoming.web;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Component
public class XiaomingErrorWriter {

    private final ObjectMapper objectMapper;
    private final XiaomingProductProperties properties;

    public XiaomingErrorWriter(ObjectMapper objectMapper, XiaomingProductProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            XiaomingErrorCode code,
            String message,
            boolean retryable
    ) throws IOException {
        XiaomingResponse<Void> body = XiaomingResponse.error(
                message,
                new XiaomingError(code, message, retryable, List.of()),
                properties.currentKnowledgeVersion(),
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request)
        );
        response.setStatus(code.getHttpStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
