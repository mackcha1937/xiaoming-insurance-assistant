package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.DocumentStatus;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Product36743ReleaseValidator {

    private static final Set<DocumentType> REQUIRED_TYPES = EnumSet.of(
            DocumentType.POLICY_TERMS,
            DocumentType.APPLICATION_RULES,
            DocumentType.APPLICATION_GUIDE,
            DocumentType.HEALTH_DISCLOSURE,
            DocumentType.OCCUPATION_TABLE,
            DocumentType.RATE_TABLE,
            DocumentType.CASH_VALUE_TABLE
    );

    public List<String> validate(KnowledgeVersion candidate, ImportedKnowledgeBundle bundle) {
        List<String> failures = new ArrayList<>();
        if (!Product36743SourceCatalog.PRODUCT_ID.equals(candidate.productId())) {
            failures.add("productId mismatch");
        }
        if (!Product36743SourceCatalog.KNOWLEDGE_VERSION.equals(candidate.versionId())) {
            failures.add("knowledge version mismatch");
        }
        if (candidate.documents().size() != Product36743SourceCatalog.sources().size()) {
            failures.add("source document count mismatch");
        }
        if (candidate.documents().stream().anyMatch(document -> document.status() != DocumentStatus.REVIEW)) {
            failures.add("all source documents must be in REVIEW");
        }
        Set<DocumentType> actualTypes = candidate.documents().stream()
                .map(document -> document.type())
                .collect(java.util.stream.Collectors.toSet());
        if (!actualTypes.containsAll(REQUIRED_TYPES)) {
            failures.add("required source types are missing");
        }
        if (bundle.chunks().isEmpty()) {
            failures.add("no semantic chunks were produced");
        }
        if (bundle.occupations().size() != 1_546) {
            failures.add("occupation dataset is incomplete");
        }
        if (bundle.rules().size() < 15) {
            failures.add("structured rules are incomplete");
        }
        if (bundle.spreadsheets().size() != 4
                || bundle.spreadsheets().stream().anyMatch(summary -> summary.sheets().isEmpty())) {
            failures.add("spreadsheet structure is incomplete");
        }
        candidate.documents().forEach(document -> {
            if (document.pageMap().isEmpty()) {
                failures.add("page map missing for " + document.documentId());
            }
            boolean hasChunk = bundle.chunks().stream()
                    .anyMatch(chunk -> chunk.documentId().equals(document.documentId()));
            if (!hasChunk) {
                failures.add("chunks missing for " + document.documentId());
            }
        });
        return List.copyOf(failures);
    }
}
