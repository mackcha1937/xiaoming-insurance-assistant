package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.DocumentType;

import java.util.Set;

public record SearchFilter(
        String productId,
        String knowledgeVersion,
        Set<DocumentType> documentTypes
) {
    public SearchFilter {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (knowledgeVersion == null || knowledgeVersion.isBlank()) {
            throw new IllegalArgumentException("knowledgeVersion must not be blank");
        }
        if (documentTypes == null || documentTypes.isEmpty()) {
            throw new IllegalArgumentException("at least one documentType is required");
        }
        documentTypes = Set.copyOf(documentTypes);
    }
}
