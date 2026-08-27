package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryBm25SearchBackendTest {

    @Test
    void ranksMatchingChineseTermsAndEnforcesThreePartScope() {
        ClauseChunk responsibility = KnowledgeFixtures.chunk("responsibility");
        ClauseChunk unrelated = new ClauseChunk(
                "unrelated",
                KnowledgeFixtures.PRODUCT_ID,
                KnowledgeFixtures.VERSION,
                "document-2",
                "投保规则.pdf",
                DocumentType.APPLICATION_RULES,
                "投保年龄",
                1,
                null,
                "投保年龄为十八周岁。",
                Set.of("年龄")
        );
        ClauseChunk otherProduct = KnowledgeFixtures.chunk(
                "other", "other", KnowledgeFixtures.VERSION, DocumentType.POLICY_TERMS
        );
        InMemoryBm25SearchBackend backend = new InMemoryBm25SearchBackend(
                List.of(unrelated, otherProduct, responsibility)
        );

        List<SearchHit> hits = backend.search(
                "保险责任怎么约定",
                new SearchFilter(
                        KnowledgeFixtures.PRODUCT_ID,
                        KnowledgeFixtures.VERSION,
                        Set.of(DocumentType.POLICY_TERMS)
                ),
                5
        );

        assertEquals(1, hits.size());
        assertEquals("responsibility", hits.getFirst().chunk().chunkId());
        assertTrue(hits.getFirst().score() > 0);
    }
}
