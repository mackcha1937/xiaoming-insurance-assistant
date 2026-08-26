package com.embabel.insurance.xiaoming.knowledge;

import java.util.ArrayList;
import java.util.List;

public class SemanticChunker {

    private final int maxCharacters;
    private final int overlapCharacters;

    public SemanticChunker(int maxCharacters, int overlapCharacters) {
        if (maxCharacters < 100) {
            throw new IllegalArgumentException("maxCharacters must be at least 100");
        }
        if (overlapCharacters < 0 || overlapCharacters >= maxCharacters) {
            throw new IllegalArgumentException("overlapCharacters must be between 0 and maxCharacters");
        }
        this.maxCharacters = maxCharacters;
        this.overlapCharacters = overlapCharacters;
    }

    public List<ClauseChunk> chunk(SourceDocument document, List<DocumentSection> sections) {
        List<ClauseChunk> chunks = new ArrayList<>();
        int sequence = 1;
        for (DocumentSection section : sections) {
            List<String> pieces = split(section.text());
            String carry = "";
            for (String piece : pieces) {
                String text = carry.isBlank() ? piece : carry + piece;
                chunks.add(new ClauseChunk(
                        document.documentId() + "-" + sequence++,
                        document.productId(),
                        document.knowledgeVersion(),
                        document.documentId(),
                        document.name(),
                        document.type(),
                        section.section(),
                        section.pdfPage(),
                        section.printedPage(),
                        text.trim(),
                        section.tags()
                ));
                carry = overlapCharacters == 0
                        ? ""
                        : text.substring(Math.max(0, text.length() - overlapCharacters));
            }
        }
        return List.copyOf(chunks);
    }

    private List<String> split(String text) {
        String normalized = text.replace("\r", "").trim();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int hardEnd = Math.min(start + maxCharacters, normalized.length());
            int end = hardEnd;
            if (hardEnd < normalized.length()) {
                int sentenceEnd = lastSentenceBoundary(normalized, start, hardEnd);
                if (sentenceEnd > start) {
                    end = sentenceEnd;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                result.add(piece);
            }
            start = end;
            while (start < normalized.length() && Character.isWhitespace(normalized.charAt(start))) {
                start++;
            }
        }
        return result;
    }

    private int lastSentenceBoundary(String text, int start, int end) {
        for (int index = end - 1; index >= start; index--) {
            char character = text.charAt(index);
            if ("。！？；.!?;\n".indexOf(character) >= 0) {
                return index + 1;
            }
        }
        return end;
    }
}
