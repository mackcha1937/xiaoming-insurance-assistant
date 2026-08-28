package com.embabel.insurance.xiaoming.checklist;

import com.embabel.insurance.xiaoming.api.ChecklistGenerateRequest;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecklistServiceTest {

    private KnowledgeSnapshotStore snapshotStore;
    private ChecklistService service;

    @BeforeEach
    void setUp() {
        snapshotStore = new KnowledgeSnapshotStore();
        service = new ChecklistService(snapshotStore);
        publish("knowledge-v1");
    }

    @Test
    void generatesThreeSectionsAndRequiredSuggestedItems() {
        ChecklistResult result = service.generate(request(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED"));

        assertEquals("material-checklist-v1@knowledge-v1", result.checklistVersion());
        assertEquals(3, result.items().stream().map(ChecklistItem::section).distinct().count());
        assertTrue(result.items().stream().anyMatch(ChecklistItem::required));
        assertTrue(result.items().stream().anyMatch(item -> !item.required()));
    }

    @Test
    void addsMinorRelationshipAndDesignatedBeneficiaryRequirements() {
        ChecklistResult result = service.generate(request(12, "PARENT", "NATIONAL_ID", "DESIGNATED", "NOT_CHECKED"));
        List<String> ids = result.items().stream().map(ChecklistItem::id).toList();

        assertTrue(ids.contains("insured-id"));
        assertTrue(ids.contains("relationship-proof"));
        assertTrue(ids.contains("guardian-proof"));
        assertTrue(ids.contains("beneficiary-proof"));
    }

    @Test
    void nonNationalIdAddsValidityCheck() {
        ChecklistResult result = service.generate(request(30, "SELF", "PASSPORT", "STATUTORY", "NO_EXCEPTION"));
        assertTrue(result.items().stream().anyMatch(item -> item.id().equals("document-validity")));
        assertTrue(result.items().stream().anyMatch(item -> item.label().contains("护照")));
    }

    @Test
    void healthFlagOnlyAddsTruthfulDisclosureAndNoSupplementRule() {
        ChecklistResult result = service.generate(request(30, "SELF", "NATIONAL_ID", "STATUTORY", "NEEDS_DISCLOSURE"));
        ChecklistItem disclosure = result.items().stream()
                .filter(item -> item.id().equals("truthful-health-disclosure"))
                .findFirst()
                .orElseThrow();

        assertTrue(disclosure.required());
        assertTrue(disclosure.reason().contains("不生成补件规则"));
        assertFalse(result.items().stream().anyMatch(item -> item.label().contains("病历") || item.label().contains("诊断")));
    }

    @Test
    void staleVersionStopsGeneration() {
        ChecklistServiceException exception = assertThrows(
                ChecklistServiceException.class,
                () -> service.generate(request(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED", "old"))
        );
        assertEquals(ChecklistServiceException.Reason.VERSION_CONFLICT, exception.reason());
        assertEquals("knowledge-v1", exception.currentKnowledgeVersion());
    }

    @Test
    void missingSnapshotFailsClosed() {
        ChecklistService emptyService = new ChecklistService(new KnowledgeSnapshotStore());
        ChecklistServiceException exception = assertThrows(
                ChecklistServiceException.class,
                () -> emptyService.generate(request(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED"))
        );
        assertEquals(ChecklistServiceException.Reason.SNAPSHOT_UNAVAILABLE, exception.reason());
    }

    private ChecklistGenerateRequest request(
            int age,
            String relationship,
            String idType,
            String beneficiary,
            String health
    ) {
        return request(age, relationship, idType, beneficiary, health, "knowledge-v1");
    }

    private ChecklistGenerateRequest request(
            int age,
            String relationship,
            String idType,
            String beneficiary,
            String health,
            String version
    ) {
        ChecklistGenerateRequest request = new ChecklistGenerateRequest();
        request.setProductId("36743");
        request.setConversationId("conversation-test-0001");
        request.setKnowledgeVersion(version);
        request.setInsuredAgeYears(age);
        request.setApplicantRelationship(relationship);
        request.setIdDocumentType(idType);
        request.setBeneficiaryMode(beneficiary);
        request.setHealthStatus(health);
        return request;
    }

    private void publish(String versionId) {
        KnowledgeVersion version = new KnowledgeVersion(
                versionId,
                "36743",
                KnowledgeVersionStatus.PUBLISHED,
                "rules-v1",
                "occupation-v1",
                Instant.parse("2026-08-28T00:00:00Z"),
                List.of()
        );
        snapshotStore.replace(version, new ImportedKnowledgeBundle(version, List.of(), List.of(), List.of(), List.of()));
    }
}
