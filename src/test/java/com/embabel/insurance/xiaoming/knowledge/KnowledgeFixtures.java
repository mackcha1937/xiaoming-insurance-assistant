package com.embabel.insurance.xiaoming.knowledge;

import com.embabel.insurance.xiaoming.retrieval.SearchHit;
import com.embabel.insurance.xiaoming.rules.BusinessRule;
import com.embabel.insurance.xiaoming.rules.ComparisonOperator;
import com.embabel.insurance.xiaoming.rules.PredicateCondition;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class KnowledgeFixtures {

    public static final String PRODUCT_ID = "36743";
    public static final String VERSION = "knowledge-2026-08";
    public static final String HASH = "a".repeat(64);

    private KnowledgeFixtures() {
    }

    public static SourceDocument document(String id, DocumentStatus status) {
        return new SourceDocument(
                id,
                PRODUCT_ID,
                VERSION,
                "正式保险条款.pdf",
                DocumentType.POLICY_TERMS,
                status,
                HASH,
                Instant.parse("2026-08-01T00:00:00Z"),
                100,
                List.of(new PageMapping(3, 1))
        );
    }

    public static ClauseChunk chunk(String id) {
        return chunk(id, PRODUCT_ID, VERSION, DocumentType.POLICY_TERMS);
    }

    public static ClauseChunk chunk(
            String id,
            String productId,
            String version,
            DocumentType type
    ) {
        return new ClauseChunk(
                id,
                productId,
                version,
                "document-1",
                "正式保险条款.pdf",
                type,
                "第二条 保险责任",
                3,
                1,
                "被保险人符合正式条款约定时，按条款承担保险责任。",
                Set.of("保障责任")
        );
    }

    public static SearchHit hit(String id, double score) {
        return new SearchHit(chunk(id), score);
    }

    public static BusinessRule rule(
            String id,
            String result,
            int priority,
            List<String> evidenceIds
    ) {
        return new BusinessRule(
                id,
                PRODUCT_ID,
                VERSION,
                "APPLICATION_RULES",
                new PredicateCondition("age", ComparisonOperator.BETWEEN, 18, 55),
                result,
                priority,
                evidenceIds
        );
    }
}
