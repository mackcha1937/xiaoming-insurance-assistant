package com.embabel.insurance.xiaoming;

import com.embabel.insurance.EmbabelApplication;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import com.embabel.insurance.xiaoming.knowledge.importer.ImportedKnowledgeBundle;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import com.embabel.insurance.xiaoming.knowledge.importer.OccupationEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EmbabelApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OccupationContractIntegrationTest {

    private static final String BASE_PATH = "/api/v1/xiaoming/occupations";

    static {
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
        System.setProperty("embabel.agent.shell.web-application-type", "servlet");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeSnapshotStore snapshotStore;

    @BeforeEach
    void publishOccupationFixture() {
        KnowledgeVersion version = version("knowledge-occupation-v1", "occupation-v1");
        List<OccupationEntry> occupations = List.of(
                occupation("10000001", "程序设计", "计算机程序设计员", 1),
                occupation("10000002", "软件工程", "计算机软件工程技术人员", 1),
                occupation("10000003", "驾驶人员", "出租车司机", 4)
        );
        snapshotStore.replace(version, new ImportedKnowledgeBundle(
                version,
                List.of(),
                List.of(),
                occupations,
                List.of()
        ));
    }

    @Test
    void searchesCandidatesWithoutCategoryAndEchoesQueryToken() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/search")
                        .queryParam("productId", "36743")
                        .queryParam("q", "程序员")
                        .queryParam("queryToken", "query-token-0001")
                        .header("X-Request-Id", "request-occupation-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.intent").value("OCCUPATION_QUERY"))
                .andExpect(jsonPath("$.data.queryToken").value("query-token-0001"))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                .andExpect(jsonPath("$.data.candidates[0].occupationId").value("10000001"))
                .andExpect(jsonPath("$.data.candidates[0].category").doesNotExist())
                .andExpect(jsonPath("$.data.occupationVersion").value("occupation-v1"))
                .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.requestId").value("request-occupation-0001"));
    }

    @Test
    void asksForOneOccupationAtATime() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/search")
                        .queryParam("productId", "36743")
                        .queryParam("q", "程序员和出租车司机")
                        .queryParam("queryToken", "query-token-0002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_INPUT"))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)))
                .andExpect(jsonPath("$.missingFields[0].name").value("occupationDescription"));
    }

    @Test
    void confirmsExactIdAndReplaysSameIdempotentRequest() throws Exception {
        String body = """
                {"productId":"36743","occupationId":"10000001","knowledgeVersion":"knowledge-occupation-v1"}
                """;
        String key = "idem-occupation-0001";

        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.category").value(1))
                .andExpect(jsonPath("$.data.occupationVersion").value("occupation-v1"))
                .andExpect(jsonPath("$.data.notice").value(org.hamcrest.Matchers.containsString("不代表保险公司最终承保结论")));

        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));
    }

    @Test
    void rejectsClientSuppliedConclusionFieldsAndIdempotencyConflict() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", "idem-occupation-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"36743","occupationId":"10000001","knowledgeVersion":"knowledge-occupation-v1","category":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        String key = "idem-occupation-0003";
        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("10000001", "knowledge-occupation-v1")))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("10000002", "knowledge-occupation-v1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsNoMatchAndStopsStaleVersionConfirmation() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", "idem-occupation-0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("99999999", "knowledge-occupation-v1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matched").value(false))
                .andExpect(jsonPath("$.data.category").doesNotExist());

        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .header("Idempotency-Key", "idem-occupation-0005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("10000001", "knowledge-old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_CONFLICT"));
    }

    @Test
    void rejectsInvalidSearchAndConfirmRequests() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/search")
                        .queryParam("productId", "other")
                        .queryParam("q", "程序员")
                        .queryParam("queryToken", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE_PATH + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("10000001", "knowledge-occupation-v1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("Idempotency-Key"));
    }

    @Test
    void publishesOccupationPathsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/occupations/search']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/occupations/confirm']").exists());
    }

    private String confirmBody(String occupationId, String knowledgeVersion) {
        return """
                {"productId":"36743","occupationId":"%s","knowledgeVersion":"%s"}
                """.formatted(occupationId, knowledgeVersion);
    }

    private KnowledgeVersion version(String knowledgeVersion, String occupationVersion) {
        return new KnowledgeVersion(
                knowledgeVersion,
                "36743",
                KnowledgeVersionStatus.PUBLISHED,
                "rules-v1",
                occupationVersion,
                Instant.parse("2026-08-28T00:00:00Z"),
                List.of()
        );
    }

    private OccupationEntry occupation(String id, String minor, String fourth, int category) {
        return new OccupationEntry(
                id,
                "occupation-v1",
                "一般职业人员",
                "测试分类",
                minor,
                fourth,
                category,
                false
        );
    }
}
