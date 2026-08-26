package com.embabel.insurance.xiaoming.contract;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "小明 V2.0 统一响应包")
public record XiaomingResponse<T>(
        BusinessStatus status,
        XiaomingIntent intent,
        String message,
        T data,
        List<XiaomingCard> cards,
        List<XiaomingEvidence> evidence,
        List<XiaomingSuggestion> suggestions,
        List<XiaomingAction> actions,
        List<MissingField> missingFields,
        String knowledgeVersion,
        String traceId,
        String requestId,
        XiaomingError error
) {
    public XiaomingResponse {
        cards = cards == null ? List.of() : List.copyOf(cards);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        actions = actions == null ? List.of() : List.copyOf(actions);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }

    public static <T> XiaomingResponse<T> success(
            String message,
            T data,
            String knowledgeVersion,
            String traceId,
            String requestId
    ) {
        return new XiaomingResponse<>(
                BusinessStatus.SUCCESS,
                null,
                message,
                data,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                knowledgeVersion,
                traceId,
                requestId,
                null
        );
    }

    public static <T> XiaomingResponse<T> error(
            String message,
            XiaomingError error,
            String knowledgeVersion,
            String traceId,
            String requestId
    ) {
        return new XiaomingResponse<>(
                BusinessStatus.ERROR,
                null,
                message,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                knowledgeVersion,
                traceId,
                requestId,
                error
        );
    }
}
