package com.embabel.insurance.xiaoming.retrieval;

import java.util.List;

public record RetrievalResult(
        List<RankedChunk> chunks,
        RetrievalMode mode,
        boolean degraded,
        double evidenceThreshold
) {
    public RetrievalResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
