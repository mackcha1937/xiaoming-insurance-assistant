package com.embabel.insurance.xiaoming;

import com.embabel.insurance.EmbabelApplication;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EmbabelApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistContractIntegrationTest {

    private static final String PATH = "/api/v1/xiaoming/checklists/generate";

    static {
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
        System.setProperty("embabel.agent.shell.web-application-type", "servlet");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private KnowledgeSnapshotStore snapshotStore;

    @BeforeEach
    void publishFixture() {
        KnowledgeVersion version = new KnowledgeVersion(
                "knowledge-checklist-v1",
                "36743",
                KnowledgeVersionStatus.PUBLISHED,
                "rules-v1",
                "occupation-v1",
                Instant.parse("2026-08-28T00:00:00Z"),
                List.of()
        );
        snapshotStore.replace(version, new ImportedKnowledgeBundle(version, List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void generatesVersionedChecklistAndReplaysSameRequest() throws Exception {
        String body = body(12, "PARENT", "NATIONAL_ID", "DESIGNATED", "NEEDS_DISCLOSURE", "knowledge-checklist-v1");
        String key = "idem-checklist-0001";

        mockMvc.perform(post(PATH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.intent").value("MATERIAL_CHECKLIST"))
                .andExpect(jsonPath("$.data.checklistVersion", containsString("knowledge-checklist-v1")))
                .andExpect(jsonPath("$.data.items[*].section", hasItem("INPUT_INFORMATION")))
                .andExpect(jsonPath("$.data.items[*].section", hasItem("PREPARE_MATERIALS")))
                .andExpect(jsonPath("$.data.items[*].section", hasItem("READ_CONFIRM")))
                .andExpect(jsonPath("$.data.items[*].id", hasItem("guardian-proof")))
                .andExpect(jsonPath("$.data.items[*].id", hasItem("beneficiary-proof")))
                .andExpect(jsonPath("$.data.items[*].id", hasItem("truthful-health-disclosure")));

        mockMvc.perform(post(PATH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }

    @Test
    void rejectsUnknownConclusionOrIdentityFields() throws Exception {
        mockMvc.perform(post(PATH).header("Idempotency-Key", "idem-checklist-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED",
                                "knowledge-checklist-v1").replace("}", ",\"idNumber\":\"secret\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validatesRequiredFieldsAndEnums() throws Exception {
        mockMvc.perform(post(PATH).header("Idempotency-Key", "idem-checklist-0003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(121, "INVALID", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED",
                                "knowledge-checklist-v1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields", hasSize(2)));
    }

    @Test
    void stopsStaleKnowledgeVersion() throws Exception {
        mockMvc.perform(post(PATH).header("Idempotency-Key", "idem-checklist-0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED", "old-version")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsMissingAndConflictingIdempotencyKeys() throws Exception {
        String first = body(30, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED", "knowledge-checklist-v1");
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isBadRequest());

        String key = "idem-checklist-0005";
        mockMvc.perform(post(PATH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(31, "SELF", "NATIONAL_ID", "STATUTORY", "NOT_CHECKED", "knowledge-checklist-v1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void exposesChecklistInContextAndOpenApi() throws Exception {
        mockMvc.perform(get("/api/v1/xiaoming/product-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities[5].intent").value("MATERIAL_CHECKLIST"))
                .andExpect(jsonPath("$.data.capabilities[5].enabled").value(true));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/checklists/generate']").exists());
    }

    private String body(int age, String relationship, String idType, String beneficiary, String health, String version) {
        return """
                {"productId":"36743","conversationId":"conversation-checklist-0001","knowledgeVersion":"%s",
                "insuredAgeYears":%d,"applicantRelationship":"%s","idDocumentType":"%s",
                "beneficiaryMode":"%s","healthStatus":"%s"}
                """.formatted(version, age, relationship, idType, beneficiary, health);
    }
}
