package com.embabel.insurance.xiaoming.knowledge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageMapService {

    public Map<Integer, Integer> validateAndIndex(List<PageMapping> mappings, int totalPdfPages) {
        if (totalPdfPages < 1) {
            throw new IllegalArgumentException("totalPdfPages must be positive");
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (PageMapping mapping : mappings) {
            if (mapping.pdfPage() > totalPdfPages) {
                throw new IllegalArgumentException("page mapping exceeds PDF page count");
            }
            if (result.containsKey(mapping.pdfPage())) {
                throw new IllegalArgumentException("duplicate PDF page mapping");
            }
            if (mapping.printedPage() != null) {
                result.put(mapping.pdfPage(), mapping.printedPage());
            }
        }
        return Map.copyOf(result);
    }
}
