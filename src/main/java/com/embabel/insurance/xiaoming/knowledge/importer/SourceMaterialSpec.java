package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.DocumentType;

import java.time.Instant;

public record SourceMaterialSpec(
        String documentId,
        String fileName,
        DocumentType documentType,
        String sha256,
        Instant effectiveAt,
        int priority
) {
    public SourceMaterialSpec {
        requireText(documentId, "documentId");
        requireText(fileName, "fileName");
        if (documentType == null) {
            throw new IllegalArgumentException("documentType must not be null");
        }
        if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
        }
        if (effectiveAt == null) {
            throw new IllegalArgumentException("effectiveAt must not be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
