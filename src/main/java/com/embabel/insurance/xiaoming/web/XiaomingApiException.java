package com.embabel.insurance.xiaoming.web;

import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;

import java.util.List;

public class XiaomingApiException extends RuntimeException {

    private final XiaomingErrorCode code;
    private final boolean retryable;
    private final List<XiaomingError.FieldViolation> fields;

    public XiaomingApiException(XiaomingErrorCode code, String message) {
        this(code, message, false, List.of());
    }

    public XiaomingApiException(
            XiaomingErrorCode code,
            String message,
            boolean retryable,
            List<XiaomingError.FieldViolation> fields
    ) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.fields = List.copyOf(fields);
    }

    public XiaomingErrorCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<XiaomingError.FieldViolation> getFields() {
        return fields;
    }
}
