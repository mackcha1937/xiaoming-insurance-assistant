package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RrfFusionService {

    public List<RankedChunk> fuse(
            List<SearchHit> bm25,
            List<SearchHit> vector,
            int topK,
            int rrfK
    ) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be positive");
        }

        Map<String, Accumulator> scores = new LinkedHashMap<>();
        accumulate(scores, bm25, rrfK, true);
        accumulate(scores, vector, rrfK, false);
        return scores.values().stream()
                .map(Accumulator::toRankedChunk)
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed()
                        .thenComparing(result -> result.chunk().chunkId()))
                .limit(topK)
                .toList();
    }

    private void accumulate(
            Map<String, Accumulator> scores,
            List<SearchHit> hits,
            int rrfK,
            boolean bm25
    ) {
        Map<String, SearchHit> unique = new LinkedHashMap<>();
        for (SearchHit hit : hits == null ? List.<SearchHit>of() : hits) {
            unique.putIfAbsent(hit.chunk().chunkId(), hit);
        }
        int rank = 1;
        for (SearchHit hit : unique.values()) {
            Accumulator accumulator = scores.computeIfAbsent(
                    hit.chunk().chunkId(),
                    ignored -> new Accumulator(hit.chunk())
            );
            accumulator.score += 1.0d / (rrfK + rank);
            if (bm25) {
                accumulator.bm25Match = true;
            } else {
                accumulator.vectorMatch = true;
            }
            rank++;
        }
    }

    private static final class Accumulator {
        private final ClauseChunk chunk;
        private double score;
        private boolean bm25Match;
        private boolean vectorMatch;

        private Accumulator(ClauseChunk chunk) {
            this.chunk = chunk;
        }

        private RankedChunk toRankedChunk() {
            return new RankedChunk(chunk, score, bm25Match, vectorMatch);
        }
    }
}
