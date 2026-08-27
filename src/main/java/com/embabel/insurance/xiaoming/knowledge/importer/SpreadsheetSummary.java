package com.embabel.insurance.xiaoming.knowledge.importer;

import java.util.List;

public record SpreadsheetSummary(
        String fileName,
        List<SheetSummary> sheets
) {
    public SpreadsheetSummary {
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
    }

    public record SheetSummary(
            String name,
            long nonEmptyRows,
            long nonEmptyCells,
            List<String> sampleRows
    ) {
        public SheetSummary {
            sampleRows = sampleRows == null ? List.of() : List.copyOf(sampleRows);
        }
    }
}
