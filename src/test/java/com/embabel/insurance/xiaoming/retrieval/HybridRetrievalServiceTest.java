package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRetrievalServiceTest {

    private final SearchFilter filter = new SearchFilter(
            KnowledgeFixtures.PRODUCT_ID,
            KnowledgeFixtures.VERSION,
            Set.of(DocumentType.POLICY_TERMS)
    );

    @Test
    void returnsHybridResultsWhenBothBackendsSucceed() {
        HybridRetrievalService service = service(
                returning(KnowledgeFixtures.hit("a", 10)),
                returning(KnowledgeFixtures.hit("a", 0.9))
        );

        RetrievalResult result = service.search("保障责任", filter, 5);

        assertEquals(RetrievalMode.HYBRID, result.mode());
        assertFalse(result.degraded());
        assertEquals(1, result.chunks().size());
    }

    @Test
    void filtersCrossProductOldVersionAndWrongDocumentTypeAfterBackendReturns() {
        ClauseChunk crossProduct = KnowledgeFixtures.chunk(
                "cross-product", "other", KnowledgeFixtures.VERSION, DocumentType.POLICY_TERMS
        );
        ClauseChunk oldVersion = KnowledgeFixtures.chunk(
                "old-version", KnowledgeFixtures.PRODUCT_ID, "old", DocumentType.POLICY_TERMS
        );
        ClauseChunk wrongType = KnowledgeFixtures.chunk(
                "wrong-type", KnowledgeFixtures.PRODUCT_ID, KnowledgeFixtures.VERSION,
                DocumentType.HEALTH_DISCLOSURE
        );
        HybridRetrievalService service = service(
                returning(
                        KnowledgeFixtures.hit("allowed", 10),
                        new SearchHit(crossProduct, 9),
                        new SearchHit(oldVersion, 8),
                        new SearchHit(wrongType, 7)
                ),
                returning()
        );

        RetrievalResult result = service.search("保障责任", filter, 10);

        assertEquals(
                List.of("allowed"),
                result.chunks().stream().map(item -> item.chunk().chunkId()).toList()
        );
    }

    @Test
    void safelyDegradesToBm25WhenVectorFails() {
        HybridRetrievalService service = service(
                returning(KnowledgeFixtures.hit("a", 10)),
                failing("vector down")
        );

        RetrievalResult result = service.search("保障责任", filter, 5);

        assertEquals(RetrievalMode.BM25_ONLY, result.mode());
        assertTrue(result.degraded());
        assertEquals(1, result.chunks().size());
    }

    @Test
    void safelyDegradesToVectorAndRaisesRawScoreThresholdWhenBm25Fails() {
        HybridRetrievalService service = service(
                failing("bm25 down"),
                returning(KnowledgeFixtures.hit("low", 0.79), KnowledgeFixtures.hit("high", 0.8))
        );

        RetrievalResult result = service.search("保障责任", filter, 5);

        assertEquals(RetrievalMode.VECTOR_ONLY, result.mode());
        assertTrue(result.degraded());
        assertEquals(List.of("high"), result.chunks().stream()
                .map(item -> item.chunk().chunkId())
                .toList());
    }

    @Test
    void stopsWhenBothBackendsFail() {
        HybridRetrievalService service = service(failing("bm25"), failing("vector"));

        assertThrows(
                RetrievalDependencyException.class,
                () -> service.search("保障责任", filter, 5)
        );
    }

    @Test
    void treatsEmptySuccessfulResultsAsInsufficientDataNotDependencyFailure() {
        HybridRetrievalService service = service(returning(), returning());

        RetrievalResult result = service.search("保障责任", filter, 5);

        assertEquals(RetrievalMode.HYBRID, result.mode());
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void validatesQueryAndFilterScope() {
        HybridRetrievalService service = service(returning(), returning());

        assertThrows(IllegalArgumentException.class, () -> service.search(" ", filter, 5));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchFilter("36743", "version", Set.of())
        );
    }

    private HybridRetrievalService service(SearchBackend bm25, SearchBackend vector) {
        return new HybridRetrievalService(
                bm25,
                vector,
                new RrfFusionService(),
                60,
                0.01,
                0.8
        );
    }

    private SearchBackend returning(SearchHit... hits) {
        return (query, searchFilter, topK) -> List.of(hits);
    }

    private SearchBackend failing(String message) {
        return (query, searchFilter, topK) -> {
            throw new RetrievalDependencyException(message);
        };
    }
}
