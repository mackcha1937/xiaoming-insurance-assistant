package com.embabel.insurance.xiaoming.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageMapAndSemanticChunkerTest {

    private final PageMapService pageMapService = new PageMapService();

    @Test
    void indexesPdfAndPrintedPagesWithoutInventingMissingPrintedPages() {
        Map<Integer, Integer> map = pageMapService.validateAndIndex(
                List.of(new PageMapping(1, null), new PageMapping(3, 1)),
                5
        );

        assertEquals(1, map.size());
        assertEquals(1, map.get(3));
        assertNull(map.get(1));
    }

    @Test
    void rejectsDuplicateOrOutOfRangeMappings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> pageMapService.validateAndIndex(
                        List.of(new PageMapping(1, 1), new PageMapping(1, 2)),
                        2
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> pageMapService.validateAndIndex(List.of(new PageMapping(3, 1)), 2)
        );
    }

    @Test
    void rejectsInvalidPageNumbersAndChunkConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PageMapping(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PageMapping(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemanticChunker(99, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemanticChunker(100, 100));
    }

    @Test
    void chunksWithinASectionAndPreservesTraceability() {
        SourceDocument document = KnowledgeFixtures.document("document-1", DocumentStatus.REVIEW);
        SemanticChunker chunker = new SemanticChunker(100, 10);
        String text = "第一句说明保障责任。".repeat(12);

        List<ClauseChunk> chunks = chunker.chunk(
                document,
                List.of(new DocumentSection("第二条", 3, 1, text, Set.of("责任")))
        );

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= 110));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.pdfPage() == 3));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.printedPage() == 1));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.section().equals("第二条")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.knowledgeVersion().equals(KnowledgeFixtures.VERSION)));
    }

    @Test
    void neverMergesTextAcrossPageSections() {
        SourceDocument document = KnowledgeFixtures.document("document-1", DocumentStatus.REVIEW);
        SemanticChunker chunker = new SemanticChunker(100, 0);

        List<ClauseChunk> chunks = chunker.chunk(
                document,
                List.of(
                        new DocumentSection("第一页", 1, null, "A".repeat(120), Set.of()),
                        new DocumentSection("第二页", 2, null, "B".repeat(120), Set.of())
                )
        );

        assertTrue(chunks.stream().filter(chunk -> chunk.pdfPage() == 1)
                .allMatch(chunk -> !chunk.text().contains("B")));
        assertTrue(chunks.stream().filter(chunk -> chunk.pdfPage() == 2)
                .allMatch(chunk -> !chunk.text().contains("A")));
    }
}
