package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;

public record SearchHit(ClauseChunk chunk, double score) {
    public SearchHit {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk must not be null");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
