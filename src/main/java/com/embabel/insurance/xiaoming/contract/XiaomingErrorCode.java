package com.embabel.insurance.xiaoming.contract;

import org.springframework.http.HttpStatus;

public enum XiaomingErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INTENT_NOT_ALLOWED(HttpStatus.OK),
    EVIDENCE_INSUFFICIENT(HttpStatus.OK),
    KNOWLEDGE_CONFLICT(HttpStatus.OK),
    FILE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    MODEL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    APPLICATION_URL_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus httpStatus;

    XiaomingErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
