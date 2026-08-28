package com.embabel.insurance.xiaoming;

import com.embabel.insurance.EmbabelApplication;
import com.embabel.insurance.xiaoming.knowledge.ClauseChunk;
import com.embabel.insurance.xiaoming.knowledge.DocumentType;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EmbabelApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthContractIntegrationTest {

    private static final String EXTRACT = "/api/v1/xiaoming/health/extract";
    private static final String MATCH = "/api/v1/xiaoming/health/match";

    static {
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
        System.setProperty("embabel.agent.shell.web-application-type", "servlet");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private KnowledgeSnapshotStore snapshotStore;

    @BeforeEach
    void publishFixture() {
        KnowledgeVersion version = new KnowledgeVersion(
                "knowledge-health-v1", "36743", KnowledgeVersionStatus.PUBLISHED,
                "rules-health-v1", "occupation-v1", Instant.parse("2026-08-28T00:00:00Z"), List.of()
        );
        ClauseChunk chunk = new ClauseChunk(
                "health-evidence-0001", "36743", "knowledge-health-v1", "doc-21",
                "21-健康告知预览.txt", DocumentType.HEALTH_DISCLOSURE, "健康告知", 1, null,
                "健康告知仅供预览 就医行为及保险情况 目前或曾经患有 最近1年内是否存在过 "
                        + "以下一项或几项检查异常 两周岁（含）以下儿童告知 16周岁（含）以上女性告知 职业情况",
                Set.of("健康")
        );
        snapshotStore.replace(version,
                new ImportedKnowledgeBundle(version, List.of(chunk), List.of(), List.of(), List.of()));
    }

    @Test
    void authorizedTextExtractionReturnsUnconfirmedDraftAndReplays() throws Exception {
        String key = "idem-health-extract-0001";
        var request = multipart(EXTRACT)
                .file(new MockMultipartFile("text", "", "text/plain",
                        "2025年3月因高血压住院，目前服药。".getBytes(StandardCharsets.UTF_8)))
                .param("productId", "36743")
                .param("conversationId", "conversation-health-0001")
                .param("knowledgeVersion", "knowledge-health-v1")
                .param("authorized", "true")
                .header("Idempotency-Key", key);

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.intent").value("HEALTH_DISCLOSURE"))
                .andExpect(jsonPath("$.data.extractionVersion", containsString("knowledge-health-v1")))
                .andExpect(jsonPath("$.data.facts[*].code", hasItem("HYPERTENSION")))
                .andExpect(jsonPath("$.data.facts[*].code", hasItem("HOSPITALIZATION")))
                .andExpect(jsonPath("$.data.facts[0].confirmed").value(false));

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }

    @Test
    void refusesExtractionWithoutAuthorizationAndUnknownParts() throws Exception {
        mockMvc.perform(multipart(EXTRACT)
                        .param("productId", "36743")
                        .param("conversationId", "conversation-health-0001")
                        .param("knowledgeVersion", "knowledge-health-v1")
                        .param("authorized", "false")
                        .header("Idempotency-Key", "idem-health-extract-0002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(multipart(EXTRACT)
                        .param("productId", "36743")
                        .param("conversationId", "conversation-health-0001")
                        .param("knowledgeVersion", "knowledge-health-v1")
                        .param("authorized", "true")
                        .param("idNumber", "secret")
                        .header("Idempotency-Key", "idem-health-extract-0003"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void imageOcrFailureReturnsSafeNeedInputWithoutFacts() throws Exception {
        MockMultipartFile image = new MockMultipartFile("files", "scan.png", "image/png", png());
        mockMvc.perform(multipart(EXTRACT).file(image)
                        .param("productId", "36743")
                        .param("conversationId", "conversation-health-0001")
                        .param("knowledgeVersion", "knowledge-health-v1")
                        .param("authorized", "true")
                        .header("Idempotency-Key", "idem-health-extract-0004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_INPUT"))
                .andExpect(jsonPath("$.data.facts.length()").value(0))
                .andExpect(jsonPath("$.data.files[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.files[0].message", containsString("OCR")));
    }

    @Test
    void confirmedFactsMatchOnlyThreeStateContractAndReplay() throws Exception {
        String key = "idem-health-match-0001";
        String body = matchBody(true, "高血压", "PRESENT", false);
        mockMvc.perform(post(MATCH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.state").value("HIT"))
                .andExpect(jsonPath("$.data.questions[0].questionId").value("2"))
                .andExpect(jsonPath("$.data.questions[0].evidence.documentName").value("21-健康告知预览.txt"))
                .andExpect(jsonPath("$.data.notice", containsString("不是核保结论")));

        mockMvc.perform(post(MATCH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mockMvc.perform(post(MATCH).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(true, "否认高血压", "ABSENT", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnconfirmedUnknownSensitiveAndStaleMatchRequests() throws Exception {
        mockMvc.perform(post(MATCH).header("Idempotency-Key", "idem-health-match-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(false, "高血压", "PRESENT", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(MATCH).header("Idempotency-Key", "idem-health-match-0003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(true, "高血压", "PRESENT", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(MATCH).header("Idempotency-Key", "idem-health-match-0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(true, "高血压 13812345678", "PRESENT", false)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(MATCH).header("Idempotency-Key", "idem-health-match-0005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(true, "高血压", "PRESENT", false)
                                .replace("knowledge-health-v1", "old-version")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_CONFLICT"));
    }

    @Test
    void exposesHealthCapabilityAndOpenApiPaths() throws Exception {
        mockMvc.perform(get("/api/v1/xiaoming/product-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities[6].intent").value("HEALTH_DISCLOSURE"))
                .andExpect(jsonPath("$.data.capabilities[6].enabled").value(true));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/health/extract']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/health/match']").exists());
    }

    private String matchBody(boolean confirmed, String value, String polarity, boolean unknown) {
        String source = "{\"sourceId\":\"manual-text\",\"sourceLabel\":\"手工文字\",\"line\":1,\"excerpt\":\"%s\"}"
                .formatted(value);
        String fact = ("{\"factId\":\"health-fact-0001\",\"type\":\"DISEASE\",\"code\":\"HYPERTENSION\","
                + "\"label\":\"高血压\",\"value\":\"%s\",\"polarity\":\"%s\",\"sourceLocation\":%s,"
                + "\"confidence\":0.95,\"confirmed\":%s%s}")
                .formatted(value, polarity, source, confirmed, unknown ? ",\"diagnosis\":\"secret\"" : "");
        return ("{\"productId\":\"36743\",\"conversationId\":\"conversation-health-0001\","
                + "\"knowledgeVersion\":\"knowledge-health-v1\",\"confirmed\":%s,\"facts\":[%s]}")
                .formatted(confirmed, fact);
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, Color.WHITE.getRGB());
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
