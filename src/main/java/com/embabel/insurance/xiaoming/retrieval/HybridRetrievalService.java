package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;

import java.util.List;

public class HybridRetrievalService {

    private final SearchBackend bm25Backend;
    private final SearchBackend vectorBackend;
    private final RrfFusionService fusionService;
    private final int rrfK;
    private final double standardEvidenceThreshold;
    private final double degradedVectorEvidenceThreshold;

    public HybridRetrievalService(
            SearchBackend bm25Backend,
            SearchBackend vectorBackend,
            RrfFusionService fusionService,
            int rrfK,
            double standardEvidenceThreshold,
            double degradedVectorEvidenceThreshold
    ) {
        this.bm25Backend = bm25Backend;
        this.vectorBackend = vectorBackend;
        this.fusionService = fusionService;
        this.rrfK = rrfK;
        this.standardEvidenceThreshold = standardEvidenceThreshold;
        this.degradedVectorEvidenceThreshold = degradedVectorEvidenceThreshold;
    }

    public RetrievalResult search(String query, SearchFilter filter, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        BackendAttempt bm25 = attempt(bm25Backend, query, filter, topK);
        BackendAttempt vector = attempt(vectorBackend, query, filter, topK);
        if (bm25.failed() && vector.failed()) {
            throw new RetrievalDependencyException("BM25 and vector retrieval are unavailable");
        }

        List<SearchHit> filteredBm25 = filter(bm25.hits(), filter);
        List<SearchHit> filteredVector = filter(vector.hits(), filter);
        if (vector.failed()) {
            return new RetrievalResult(
                    fusionService.fuse(filteredBm25, List.of(), topK, rrfK),
                    RetrievalMode.BM25_ONLY,
                    true,
                    standardEvidenceThreshold
            );
        }
        if (bm25.failed()) {
            List<SearchHit> strictVector = filteredVector.stream()
                    .filter(hit -> hit.score() >= degradedVectorEvidenceThreshold)
                    .toList();
            return new RetrievalResult(
                    fusionService.fuse(List.of(), strictVector, topK, rrfK),
                    RetrievalMode.VECTOR_ONLY,
                    true,
                    standardEvidenceThreshold
            );
        }
        return new RetrievalResult(
                fusionService.fuse(filteredBm25, filteredVector, topK, rrfK),
                RetrievalMode.HYBRID,
                false,
                standardEvidenceThreshold
        );
    }

    private BackendAttempt attempt(
            SearchBackend backend,
            String query,
            SearchFilter filter,
            int topK
    ) {
        try {
            List<SearchHit> hits = backend.search(query, filter, topK);
            return new BackendAttempt(hits == null ? List.of() : List.copyOf(hits), false);
        } catch (RetrievalDependencyException exception) {
            return new BackendAttempt(List.of(), true);
        }
    }

    private List<SearchHit> filter(List<SearchHit> hits, SearchFilter filter) {
        return hits.stream()
                .filter(hit -> matches(hit.chunk(), filter))
                .toList();
    }

    private boolean matches(ClauseChunk chunk, SearchFilter filter) {
        return chunk.productId().equals(filter.productId())
                && chunk.knowledgeVersion().equals(filter.knowledgeVersion())
                && filter.documentTypes().contains(chunk.documentType());
    }

    private record BackendAttempt(List<SearchHit> hits, boolean failed) {
    }
}
