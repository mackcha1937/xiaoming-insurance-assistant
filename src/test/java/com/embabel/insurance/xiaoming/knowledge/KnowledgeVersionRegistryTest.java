package com.embabel.insurance.xiaoming.knowledge;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeVersionRegistryTest {

    private KnowledgeVersionRegistry registry;

    @BeforeEach
    void setUp() {
        XiaomingProductProperties properties = new XiaomingProductProperties(
                KnowledgeFixtures.PRODUCT_ID,
                "产品",
                "baseline",
                "test"
        );
        registry = new KnowledgeVersionRegistry(
                properties,
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void seedsOnlyTheConfiguredPublishedBaseline() {
        assertEquals("baseline", registry.current().versionId());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, registry.current().status());
        assertEquals(1, registry.history().size());
    }

    @Test
    void rejectsCrossProductDrafts() {
        KnowledgeVersion draft = draft("other", "other-product");

        assertThrows(KnowledgeReleaseException.class, () -> registry.registerDraft(draft));
    }

    @Test
    void rejectsDuplicateVersionsAndInvalidTransitions() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));

        assertThrows(
                KnowledgeReleaseException.class,
                () -> registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID))
        );
        assertThrows(KnowledgeReleaseException.class, () -> registry.publish("v2", ignored -> List.of()));
    }

    @Test
    void rejectsDuplicateFilesAndDocumentVersionLeaks() {
        SourceDocument first = documentFor("first", "v2", KnowledgeFixtures.HASH);
        SourceDocument duplicateHash = documentFor("second", "v2", KnowledgeFixtures.HASH);
        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeVersion(
                        "v2",
                        KnowledgeFixtures.PRODUCT_ID,
                        KnowledgeVersionStatus.DRAFT,
                        "rules",
                        null,
                        null,
                        List.of(first, duplicateHash)
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new KnowledgeVersion(
                        "v3",
                        KnowledgeFixtures.PRODUCT_ID,
                        KnowledgeVersionStatus.DRAFT,
                        "rules",
                        null,
                        null,
                        List.of(first)
                )
        );
    }

    @Test
    void validationFailureKeepsThePreviousVersionPublished() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));
        registry.submitForReview("v2");

        assertThrows(
                KnowledgeReleaseException.class,
                () -> registry.publish("v2", ignored -> List.of("page check failed"))
        );

        assertEquals("baseline", registry.current().versionId());
        assertEquals(KnowledgeVersionStatus.REVIEW, registry.find(KnowledgeFixtures.PRODUCT_ID, "v2").orElseThrow().status());
    }

    @Test
    void activationFailureKeepsThePreviousVersionPublished() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));
        registry.submitForReview("v2");

        assertThrows(
                KnowledgeReleaseException.class,
                () -> registry.publish(
                        "v2",
                        ignored -> List.of(),
                        ignored -> {
                            throw new IllegalStateException("snapshot unavailable");
                        }
                )
        );

        assertEquals("baseline", registry.current().versionId());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, registry.current().status());
        assertEquals(KnowledgeVersionStatus.REVIEW,
                registry.find(KnowledgeFixtures.PRODUCT_ID, "v2").orElseThrow().status());
    }

    @Test
    void publishAtomicallySwitchesVersionAndDocumentStatus() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));
        registry.submitForReview("v2");

        KnowledgeVersion published = registry.publish("v2", ignored -> List.of());

        assertEquals("v2", registry.current().versionId());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, published.status());
        assertEquals(DocumentStatus.PUBLISHED, published.documents().getFirst().status());
        assertEquals(
                KnowledgeVersionStatus.DISABLED,
                registry.find(KnowledgeFixtures.PRODUCT_ID, "baseline").orElseThrow().status()
        );
    }

    @Test
    void supportsRollbackWithoutDeletingHistory() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));
        registry.submitForReview("v2");
        registry.publish("v2", ignored -> List.of());

        KnowledgeVersion restored = registry.rollbackTo("baseline");

        assertEquals("baseline", restored.versionId());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, restored.status());
        assertEquals(KnowledgeVersionStatus.DISABLED, registry.find(KnowledgeFixtures.PRODUCT_ID, "v2").orElseThrow().status());
        assertEquals(2, registry.history().size());
    }

    @Test
    void refusesRollbackToDraftOrCurrentVersion() {
        registry.registerDraft(draft("v2", KnowledgeFixtures.PRODUCT_ID));

        assertThrows(KnowledgeReleaseException.class, () -> registry.rollbackTo("v2"));
        assertThrows(KnowledgeReleaseException.class, () -> registry.rollbackTo("baseline"));
    }

    private KnowledgeVersion draft(String versionId, String productId) {
        SourceDocument document = new SourceDocument(
                "document-1",
                productId,
                versionId,
                "正式保险条款.pdf",
                DocumentType.POLICY_TERMS,
                DocumentStatus.DRAFT,
                KnowledgeFixtures.HASH,
                Instant.parse("2026-08-01T00:00:00Z"),
                100,
                List.of(new PageMapping(3, 1))
        );
        return new KnowledgeVersion(
                versionId,
                productId,
                KnowledgeVersionStatus.DRAFT,
                versionId + "-rules",
                null,
                null,
                List.of(document)
        );
    }

    private SourceDocument documentFor(String id, String version, String hash) {
        return new SourceDocument(
                id,
                KnowledgeFixtures.PRODUCT_ID,
                version,
                id + ".pdf",
                DocumentType.POLICY_TERMS,
                DocumentStatus.DRAFT,
                hash,
                Instant.parse("2026-08-01T00:00:00Z"),
                100,
                List.of(new PageMapping(1, 1))
        );
    }
}
