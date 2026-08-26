package com.embabel.insurance.xiaoming.knowledge;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record KnowledgeVersion(
        String versionId,
        String productId,
        KnowledgeVersionStatus status,
        String rulesVersion,
        String occupationVersion,
        Instant publishedAt,
        List<SourceDocument> documents
) {
    public KnowledgeVersion {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("versionId must not be blank");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (rulesVersion == null || rulesVersion.isBlank()) {
            throw new IllegalArgumentException("rulesVersion must not be blank");
        }
        documents = documents == null ? List.of() : List.copyOf(documents);
        Set<String> documentIds = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (SourceDocument document : documents) {
            if (!productId.equals(document.productId()) || !versionId.equals(document.knowledgeVersion())) {
                throw new IllegalArgumentException("documents must belong to the same product and version");
            }
            if (!documentIds.add(document.documentId())) {
                throw new IllegalArgumentException("duplicate documentId in knowledge version");
            }
            if (!hashes.add(document.sha256())) {
                throw new IllegalArgumentException("duplicate source document hash in knowledge version");
            }
        }
    }

    public KnowledgeVersion withStatus(KnowledgeVersionStatus next, Instant publicationTime) {
        DocumentStatus documentStatus = switch (next) {
            case DRAFT -> DocumentStatus.DRAFT;
            case REVIEW -> DocumentStatus.REVIEW;
            case PUBLISHED -> DocumentStatus.PUBLISHED;
            case DISABLED -> DocumentStatus.DISABLED;
        };
        return new KnowledgeVersion(
                versionId,
                productId,
                next,
                rulesVersion,
                occupationVersion,
                publicationTime,
                documents.stream().map(document -> document.withStatus(documentStatus)).toList()
        );
    }
}
