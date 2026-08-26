package com.embabel.insurance.xiaoming.contract;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "统一错误信息")
public record XiaomingError(
        XiaomingErrorCode code,
        String message,
        boolean retryable,
        List<FieldViolation> fields
) {
    public XiaomingError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public record FieldViolation(String field, String message) {
    }
}
