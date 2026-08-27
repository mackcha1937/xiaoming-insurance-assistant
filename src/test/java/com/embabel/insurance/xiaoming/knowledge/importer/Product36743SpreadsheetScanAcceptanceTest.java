package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Product36743SpreadsheetScanAcceptanceTest {

    @Test
    void streamsEveryWorksheetWithoutLoadingTheHugeCashValueWorkbookIntoMemory() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.getBoolean("xiaoming.fullSpreadsheetScan"),
                "full source spreadsheet scan is an explicit Phase 02 acceptance check"
        );
        Path sourceDirectory = Path.of("docs/source-materials/product-36743/raw");
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Files.isRegularFile(sourceDirectory.resolve("05-主险现金价值表.xlsx")),
                "confirmed local source materials are not present"
        );

        ImportedKnowledgeBundle bundle = new Product36743SourceImporter(new SemanticChunker(1_200, 150))
                .importFrom(sourceDirectory, SpreadsheetScanMode.FULL_CELL_SCAN);

        assertEquals(4, bundle.spreadsheets().size());
        assertEquals(14, bundle.spreadsheets().stream().mapToInt(summary -> summary.sheets().size()).sum());
        bundle.spreadsheets().forEach(summary -> summary.sheets().forEach(sheet -> {
            assertTrue(sheet.nonEmptyRows() > 0, () -> summary.fileName() + "/" + sheet.name());
            assertTrue(sheet.nonEmptyCells() > 0, () -> summary.fileName() + "/" + sheet.name());
            assertFalse(sheet.sampleRows().isEmpty(), () -> summary.fileName() + "/" + sheet.name());
        }));
    }
}
