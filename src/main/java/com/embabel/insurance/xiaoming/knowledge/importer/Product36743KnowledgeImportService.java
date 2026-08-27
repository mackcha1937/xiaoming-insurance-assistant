package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;

import java.nio.file.Path;

public class Product36743KnowledgeImportService {

    private final Product36743SourceImporter importer;
    private final Product36743ReleaseValidator validator;
    private final KnowledgeVersionRegistry registry;
    private final KnowledgeSnapshotStore snapshotStore;

    public Product36743KnowledgeImportService(
            Product36743SourceImporter importer,
            Product36743ReleaseValidator validator,
            KnowledgeVersionRegistry registry,
            KnowledgeSnapshotStore snapshotStore
    ) {
        this.importer = importer;
        this.validator = validator;
        this.registry = registry;
        this.snapshotStore = snapshotStore;
    }

    public synchronized KnowledgeSnapshotStore.Snapshot importAndPublish(
            Path sourceDirectory,
            SpreadsheetScanMode spreadsheetScanMode
    ) {
        ImportedKnowledgeBundle bundle = importer.importFrom(sourceDirectory, spreadsheetScanMode);
        registry.registerDraft(bundle.knowledgeVersion());
        KnowledgeVersion review = registry.submitForReview(bundle.knowledgeVersion().versionId());
        registry.publish(
                review.versionId(),
                candidate -> validator.validate(candidate, bundle),
                candidate -> snapshotStore.replace(candidate, bundle)
        );
        return snapshotStore.current().orElseThrow();
    }
}
