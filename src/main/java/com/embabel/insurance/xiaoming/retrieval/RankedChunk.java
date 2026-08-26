package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;

public record RankedChunk(
        ClauseChunk chunk,
        double score,
        boolean bm25Match,
        boolean vectorMatch
) {
}
