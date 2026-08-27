package com.embabel.insurance.xiaoming.knowledge.importer;

import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.rules.BusinessRule;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class KnowledgeSnapshotStore {

    private final AtomicReference<Snapshot> current = new AtomicReference<>();

    public void replace(KnowledgeVersion publishedVersion, ImportedKnowledgeBundle bundle) {
        if (publishedVersion.status() != KnowledgeVersionStatus.PUBLISHED) {
            throw new IllegalArgumentException("only a published knowledge version can replace the snapshot");
        }
        if (!publishedVersion.versionId().equals(bundle.knowledgeVersion().versionId())) {
            throw new IllegalArgumentException("published version and imported bundle do not match");
        }
        current.set(new Snapshot(
                publishedVersion,
                bundle.chunks(),
                bundle.rules(),
                bundle.occupations(),
                bundle.spreadsheets()
        ));
    }

    public Optional<Snapshot> current() {
        return Optional.ofNullable(current.get());
    }

    public record Snapshot(
            KnowledgeVersion knowledgeVersion,
            List<ClauseChunk> chunks,
            List<BusinessRule> rules,
            List<OccupationEntry> occupations,
            List<SpreadsheetSummary> spreadsheets
    ) {
        public Snapshot {
            chunks = List.copyOf(chunks);
            rules = List.copyOf(rules);
            occupations = List.copyOf(occupations);
            spreadsheets = List.copyOf(spreadsheets);
        }
    }
}
