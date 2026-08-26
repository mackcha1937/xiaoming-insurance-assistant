package com.embabel.insurance.xiaoming.knowledge;

import java.util.Set;

public record ClauseChunk(
        String chunkId,
        String productId,
        String knowledgeVersion,
        String documentId,
        String documentName,
        DocumentType documentType,
        String section,
        int pdfPage,
        Integer printedPage,
        String text,
        Set<String> tags
) {
    public ClauseChunk {
        requireText(chunkId, "chunkId");
        requireText(productId, "productId");
        requireText(knowledgeVersion, "knowledgeVersion");
        requireText(documentId, "documentId");
        requireText(documentName, "documentName");
        requireText(section, "section");
        requireText(text, "text");
        if (documentType == null) {
            throw new IllegalArgumentException("documentType must not be null");
        }
        if (pdfPage < 1) {
            throw new IllegalArgumentException("pdfPage must be positive");
        }
        if (printedPage != null && printedPage < 1) {
            throw new IllegalArgumentException("printedPage must be positive when present");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
