package com.embabel.insurance.xiaoming.knowledge;

import java.util.Set;

public record DocumentSection(
        String section,
        int pdfPage,
        Integer printedPage,
        String text,
        Set<String> tags
) {
    public DocumentSection {
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section must not be blank");
        }
        if (pdfPage < 1) {
            throw new IllegalArgumentException("pdfPage must be positive");
        }
        if (printedPage != null && printedPage < 1) {
            throw new IllegalArgumentException("printedPage must be positive when present");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
