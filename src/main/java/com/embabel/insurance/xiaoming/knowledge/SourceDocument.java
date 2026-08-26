package com.embabel.insurance.xiaoming.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SourceDocument(
        String documentId,
        String productId,
        String knowledgeVersion,
        String name,
        DocumentType type,
        DocumentStatus status,
        String sha256,
        Instant effectiveAt,
        int priority,
        List<PageMapping> pageMap
) {
    public SourceDocument {
        requireText(documentId, "documentId");
        requireText(productId, "productId");
        requireText(knowledgeVersion, "knowledgeVersion");
        requireText(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        if (sha256 == null || !sha256.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        }
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        pageMap = pageMap == null ? List.of() : List.copyOf(pageMap);
    }

    public SourceDocument withStatus(DocumentStatus nextStatus) {
        return new SourceDocument(
                documentId, productId, knowledgeVersion, name, type, nextStatus,
                sha256, effectiveAt, priority, pageMap
        );
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
