package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionServiceTest {

    private final RrfFusionService service = new RrfFusionService();

    @Test
    void promotesChunksFoundByBothBackends() {
        List<RankedChunk> result = service.fuse(
                List.of(KnowledgeFixtures.hit("a", 10), KnowledgeFixtures.hit("b", 9)),
                List.of(KnowledgeFixtures.hit("b", 0.9), KnowledgeFixtures.hit("c", 0.8)),
                3,
                60
        );

        assertEquals(List.of("b", "a", "c"), ids(result));
        assertTrue(result.getFirst().bm25Match());
        assertTrue(result.getFirst().vectorMatch());
    }

    @Test
    void keepsSingleBackendHitsAndMarksTheirSource() {
        List<RankedChunk> result = service.fuse(
                List.of(KnowledgeFixtures.hit("a", 10)),
                List.of(KnowledgeFixtures.hit("b", 0.9)),
                2,
                60
        );

        assertTrue(result.get(0).bm25Match());
        assertFalse(result.get(0).vectorMatch());
        assertFalse(result.get(1).bm25Match());
        assertTrue(result.get(1).vectorMatch());
    }

    @Test
    void doesNotDoubleCountDuplicateIdsWithinOneBackend() {
        List<RankedChunk> result = service.fuse(
                List.of(KnowledgeFixtures.hit("a", 10), KnowledgeFixtures.hit("a", 9)),
                List.of(),
                5,
                60
        );

        assertEquals(1, result.size());
        assertEquals(1.0d / 61.0d, result.getFirst().score(), 0.0000001d);
    }

    @Test
    void usesChunkIdAsStableTieBreaker() {
        List<RankedChunk> result = service.fuse(
                List.of(KnowledgeFixtures.hit("b", 10)),
                List.of(KnowledgeFixtures.hit("a", 0.9)),
                2,
                60
        );

        assertEquals(List.of("a", "b"), ids(result));
    }

    @Test
    void appliesTopKAfterFusion() {
        List<RankedChunk> result = service.fuse(
                List.of(KnowledgeFixtures.hit("a", 10), KnowledgeFixtures.hit("b", 9)),
                List.of(KnowledgeFixtures.hit("b", 0.9), KnowledgeFixtures.hit("c", 0.8)),
                1,
                60
        );

        assertEquals(List.of("b"), ids(result));
    }

    @Test
    void rejectsInvalidRrfArguments() {
        assertThrows(IllegalArgumentException.class, () -> service.fuse(List.of(), List.of(), 0, 60));
        assertThrows(IllegalArgumentException.class, () -> service.fuse(List.of(), List.of(), 1, 0));
    }

    private List<String> ids(List<RankedChunk> result) {
        return result.stream().map(item -> item.chunk().chunkId()).toList();
    }
}
