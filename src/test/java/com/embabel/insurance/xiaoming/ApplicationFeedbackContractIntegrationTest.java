package com.embabel.insurance.xiaoming;

import com.embabel.insurance.EmbabelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EmbabelApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Timeout(20)
class ApplicationFeedbackContractIntegrationTest {

    static {
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
        System.setProperty("embabel.agent.shell.web-application-type", "servlet");
    }

    @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;

    @Test
    void applicationLinkDropsUnknownAndSensitiveParameters() throws Exception {
        mockMvc.perform(get("/api/v1/xiaoming/application-url")
                        .param("productId", "36743")
                        .param("channelType", "advisor")
                        .param("unknown", "discard")
                        .param("healthFacts", "never-copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("FORMAL_APPLICATION"))
                .andExpect(jsonPath("$.data.url").value(
                        "https://apply.example.test/start?productId=36743&channelType=advisor"))
                .andExpect(jsonPath("$.data.url", not(containsString("healthFacts"))))
                .andExpect(jsonPath("$.data.notice", containsString("不代表")));
    }

    @Test
    void feedbackIsIdempotentRejectsDifferentBodyAndNeverEchoesText() throws Exception {
        String key = "idem-feedback-0000001";
        String body = body("UNCLEAR", false, null);
        mockMvc.perform(post("/api/v1/xiaoming/feedback")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.feedbackId", containsString("feedback-")))
                .andExpect(jsonPath("$.data.questionSummary").doesNotExist());

        mockMvc.perform(post("/api/v1/xiaoming/feedback")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        mockMvc.perform(post("/api/v1/xiaoming/feedback")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("OUTDATED", false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void feedbackRejectsSensitiveHealthAndUnknownFields() throws Exception {
        mockMvc.perform(post("/api/v1/xiaoming/feedback")
                        .header("Idempotency-Key", "idem-feedback-0000002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", true, "手机号 13812345678")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/xiaoming/feedback")
                        .header("Idempotency-Key", "idem-feedback-0000003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OTHER", false, null).replace("}", ",\"admin\":true}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesPhaseSevenCapabilityAndOpenApiPaths() throws Exception {
        mockMvc.perform(get("/api/v1/xiaoming/product-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilities[8].intent").value("FORMAL_APPLICATION"))
                .andExpect(jsonPath("$.data.capabilities[8].enabled").value(true));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/application-url']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/feedback']").exists());
    }

    @Test
    void typedFormalApplicationReturnsPreflightHandoffWithoutAUrl() throws Exception {
        mockMvc.perform(post("/api/v1/xiaoming/chat")
                        .header("Idempotency-Key", "idem-application-chat-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"36743","conversationId":"conversation-application-0001","message":"我想开始正式投保"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEED_INPUT"))
                .andExpect(jsonPath("$.intent").value("FORMAL_APPLICATION"))
                .andExpect(jsonPath("$.data.target").value("formal-application"))
                .andExpect(jsonPath("$.cards[0].type").value("APPLICATION_CHECK"))
                .andExpect(jsonPath("$.actions[0].type").value("RUN_APPLICATION_CHECK"))
                .andExpect(jsonPath("$.data.url").doesNotExist());
    }

    private String body(String reason, boolean includeContext, String question) {
        String context = question == null ? "" : ",\"questionSummary\":\"" + question + "\"";
        return "{\"productId\":\"36743\",\"conversationId\":\"conversation-feedback-0001\","
                + "\"knowledgeVersion\":\"knowledge-health-v1\",\"intent\":\"APPLICATION_RULES\","
                + "\"reason\":\"" + reason + "\",\"includeContext\":" + includeContext
                + ",\"evidenceIds\":[\"evidence-1\"]" + context + "}";
    }
}
