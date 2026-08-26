package com.embabel.insurance.xiaoming.web;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(basePackages = "com.embabel.insurance.xiaoming.api")
public class XiaomingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(XiaomingExceptionHandler.class);

    private final XiaomingProductProperties properties;

    public XiaomingExceptionHandler(XiaomingProductProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(XiaomingApiException.class)
    public ResponseEntity<XiaomingResponse<Void>> handleApiException(
            XiaomingApiException exception,
            HttpServletRequest request
    ) {
        return error(
                exception.getCode(),
                exception.getMessage(),
                exception.isRetryable(),
                exception.getFields(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<XiaomingResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<XiaomingError.FieldViolation> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new XiaomingError.FieldViolation(error.getField(), safeValidationMessage(error.getDefaultMessage())))
                .toList();
        return error(XiaomingErrorCode.VALIDATION_ERROR, "请求字段不符合契约。", false, fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<XiaomingResponse<Void>> handleUnreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                XiaomingErrorCode.VALIDATION_ERROR,
                "请求 JSON 无法解析。",
                false,
                List.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<XiaomingResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Xiaoming request failed: traceId={}, exceptionType={}",
                XiaomingRequestContext.traceId(request), exception.getClass().getSimpleName());
        return error(
                XiaomingErrorCode.DEPENDENCY_UNAVAILABLE,
                "服务暂时不可用，请稍后重试。",
                true,
                List.of(),
                request
        );
    }

    private ResponseEntity<XiaomingResponse<Void>> error(
            XiaomingErrorCode code,
            String message,
            boolean retryable,
            List<XiaomingError.FieldViolation> fields,
            HttpServletRequest request
    ) {
        XiaomingError error = new XiaomingError(code, message, retryable, fields);
        XiaomingResponse<Void> body = XiaomingResponse.error(
                message,
                error,
                properties.currentKnowledgeVersion(),
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request)
        );
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }

    private String safeValidationMessage(String message) {
        return message == null || message.isBlank() ? "字段值无效" : message;
    }
}
