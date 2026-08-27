package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.SemanticChunker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Product36743SourceImporterSecurityTest {

    @TempDir
    Path temporaryDirectory;

    private final Product36743SourceImporter importer =
            new Product36743SourceImporter(new SemanticChunker(1_200, 150));

    @Test
    void catalogUsesDistinctImmutableHashes() {
        assertEquals(21, Product36743SourceCatalog.sources().size());
        assertEquals(21, Product36743SourceCatalog.sources().stream()
                .map(SourceMaterialSpec::sha256)
                .distinct()
                .count());
    }

    @Test
    void rejectsMissingOrHashMismatchedSourcesBeforeParsing() throws IOException {
        SourceImportException missing = assertThrows(
                SourceImportException.class,
                () -> importer.importFrom(temporaryDirectory, SpreadsheetScanMode.STRUCTURE_ONLY)
        );
        assertTrue(missing.getMessage().contains("required source file is unavailable"));

        Files.writeString(temporaryDirectory.resolve("01-主险条款.pdf"), "tampered");
        SourceImportException mismatch = assertThrows(
                SourceImportException.class,
                () -> importer.importFrom(temporaryDirectory, SpreadsheetScanMode.STRUCTURE_ONLY)
        );
        assertTrue(mismatch.getMessage().contains("source hash mismatch"));
    }
}
