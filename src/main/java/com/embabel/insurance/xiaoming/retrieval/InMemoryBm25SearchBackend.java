package com.embabel.insurance.xiaoming.retrieval;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class InMemoryBm25SearchBackend implements SearchBackend {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final int MAX_HITS_PER_DOCUMENT = 3;
    private final List<IndexedChunk> index;

    public InMemoryBm25SearchBackend(List<ClauseChunk> chunks) {
        this.index = chunks.stream().map(this::index).toList();
    }

    @Override
    public List<SearchHit> search(String query, SearchFilter filter, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        List<IndexedChunk> candidates = index.stream()
                .filter(document -> matches(document.chunk(), filter))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String token : new HashSet<>(queryTokens)) {
            int frequency = (int) candidates.stream()
                    .filter(document -> document.termFrequency().containsKey(token))
                    .count();
            documentFrequency.put(token, frequency);
        }
        double averageLength = candidates.stream()
                .mapToInt(IndexedChunk::length)
                .average()
                .orElse(1.0);
        List<SearchHit> results = new ArrayList<>();
        for (IndexedChunk document : candidates) {
            double score = score(queryTokens, documentFrequency, document, candidates.size(), averageLength);
            if (score > 0) {
                results.add(new SearchHit(document.chunk(), score));
            }
        }
        List<SearchHit> sorted = results.stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed()
                        .thenComparing(hit -> hit.chunk().chunkId()))
                .toList();
        List<SearchHit> diverse = new ArrayList<>();
        Map<String, Integer> hitsPerDocument = new HashMap<>();
        for (SearchHit hit : sorted) {
            int count = hitsPerDocument.getOrDefault(hit.chunk().documentId(), 0);
            if (count >= MAX_HITS_PER_DOCUMENT) {
                continue;
            }
            diverse.add(hit);
            hitsPerDocument.put(hit.chunk().documentId(), count + 1);
            if (diverse.size() == topK) {
                break;
            }
        }
        return List.copyOf(diverse);
    }

    private double score(
            List<String> queryTokens,
            Map<String, Integer> documentFrequency,
            IndexedChunk document,
            int documentCount,
            double averageLength
    ) {
        double score = 0.0;
        Map<String, Integer> queryFrequency = frequencies(queryTokens);
        for (Map.Entry<String, Integer> queryTerm : queryFrequency.entrySet()) {
            int termFrequency = document.termFrequency().getOrDefault(queryTerm.getKey(), 0);
            if (termFrequency == 0) {
                continue;
            }
            int matchingDocuments = documentFrequency.getOrDefault(queryTerm.getKey(), 0);
            double inverseDocumentFrequency = Math.log(
                    1.0 + (documentCount - matchingDocuments + 0.5) / (matchingDocuments + 0.5)
            );
            double normalization = termFrequency + K1 * (
                    1.0 - B + B * document.length() / Math.max(averageLength, 1.0)
            );
            score += queryTerm.getValue()
                    * inverseDocumentFrequency
                    * termFrequency * (K1 + 1.0) / normalization;
        }
        return score;
    }

    private IndexedChunk index(ClauseChunk chunk) {
        List<String> tokens = tokenize(
                chunk.documentName() + " " + chunk.section() + " " + String.join(" ", chunk.tags()) + " " + chunk.text()
        );
        return new IndexedChunk(chunk, frequencies(tokens), Math.max(tokens.size(), 1));
    }

    private Map<String, Integer> frequencies(List<String> tokens) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
        return frequencies;
    }

    private List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder han = new StringBuilder();
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(tokens, latin);
                han.appendCodePoint(codePoint);
            } else {
                flushHan(tokens, han);
                if (Character.isLetterOrDigit(codePoint)) {
                    latin.appendCodePoint(Character.toLowerCase(codePoint));
                } else {
                    flushLatin(tokens, latin);
                }
            }
        }
        flushHan(tokens, han);
        flushLatin(tokens, latin);
        return tokens;
    }

    private void flushLatin(List<String> tokens, StringBuilder value) {
        if (!value.isEmpty()) {
            tokens.add(value.toString().toLowerCase(Locale.ROOT));
            value.setLength(0);
        }
    }

    private void flushHan(List<String> tokens, StringBuilder value) {
        if (value.isEmpty()) {
            return;
        }
        int[] codePoints = value.codePoints().toArray();
        for (int codePoint : codePoints) {
            tokens.add(new String(Character.toChars(codePoint)));
        }
        for (int index = 0; index + 1 < codePoints.length; index++) {
            tokens.add(new String(codePoints, index, 2));
        }
        value.setLength(0);
    }

    private boolean matches(ClauseChunk chunk, SearchFilter filter) {
        return chunk.productId().equals(filter.productId())
                && chunk.knowledgeVersion().equals(filter.knowledgeVersion())
                && filter.documentTypes().contains(chunk.documentType());
    }

    private record IndexedChunk(ClauseChunk chunk, Map<String, Integer> termFrequency, int length) {
    }
}
