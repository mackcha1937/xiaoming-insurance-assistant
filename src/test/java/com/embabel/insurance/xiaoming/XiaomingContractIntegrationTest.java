package com.embabel.insurance.xiaoming;

import com.embabel.insurance.EmbabelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class XiaomingContractIntegrationTest {

    private static final String BASE_PATH = "/api/v1/xiaoming";

    static {
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
        System.setProperty("embabel.agent.shell.web-application-type", "servlet");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNoLoginProductContextWithSevenPhaseFiveCapabilities() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/product-context")
                        .header("X-Request-Id", "request-00000001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "request-00000001"))
                .andExpect(header().string("X-Trace-Id", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productId").value("36743"))
                .andExpect(jsonPath("$.data.name").value("复星联合达尔文12号重大疾病保险（互联网）"))
                .andExpect(jsonPath("$.data.authenticationRequired").value(false))
                .andExpect(jsonPath("$.data.accessMode").value("POC_NO_LOGIN"))
                .andExpect(jsonPath("$.data.capabilities", hasSize(9)))
                .andExpect(jsonPath("$.data.capabilities[?(@.enabled == true)]", hasSize(7)))
                .andExpect(jsonPath("$.data.capabilities[?(@.enabled == false)]", hasSize(2)))
                .andExpect(jsonPath("$.cards").isArray())
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.actions").isArray())
                .andExpect(jsonPath("$.missingFields").isArray())
                .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.requestId").value("request-00000001"))
                .andExpect(jsonPath("$.knowledgeVersion").value("v2.0-baseline"));
    }

    @Test
    void shouldReturnReadyWhenApiAndH2AreAvailable() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies.api").value("UP"))
                .andExpect(jsonPath("$.data.dependencies.database").value("UP"));
    }

    @Test
    void shouldSafelyReplaceInvalidRequestId() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/product-context")
                        .header("X-Request-Id", "bad id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", not("bad id")))
                .andExpect(jsonPath("$.requestId").value(not("bad id")));
    }

    @Test
    void shouldRejectMissingIdempotencyKeyWithUnifiedError() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChat("hello")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.requestId").value(not(blankOrNullString())));
    }

    @Test
    void shouldRejectInvalidProductBlankAndOversizedMessages() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", "idem-000000000101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"other","conversationId":"conversation-0001","message":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields", hasSize(2)));

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", "idem-000000000102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChat("x".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownFieldsAndMalformedJson() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", "idem-000000000103")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"36743","conversationId":"conversation-0001","message":"hi","intent":"FORMAL_APPLICATION"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", "idem-000000000104")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnSafePlaceholderAndReplaySameRequest() throws Exception {
        String body = validChat("保障什么");
        String key = "idem-000000000105";

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", key)
                        .header("X-Request-Id", "request-00000105")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.cards", hasSize(0)))
                .andExpect(jsonPath("$.evidence", hasSize(0)));

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", key)
                        .header("X-Request-Id", "request-00000105")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.requestId").value("request-00000105"));
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentPayload() throws Exception {
        String key = "idem-000000000106";

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChat("保障责任")))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post(BASE_PATH + "/chat")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChat("免责情形")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldPublishFoundationPathsAndEnumsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/product-context']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/health/ready']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/xiaoming/chat']").exists())
                .andExpect(jsonPath("$.components.schemas.XiaomingResponseMapStringObject.properties.status.enum", hasSize(6)))
                .andExpect(jsonPath("$.components.schemas.XiaomingResponseMapStringObject.properties.intent.enum", hasSize(9)))
                .andExpect(jsonPath("$.components.schemas.XiaomingError.properties.code.enum", hasSize(9)));
    }

    private String validChat(String message) {
        return """
                {"productId":"36743","conversationId":"conversation-0001","message":"%s","context":{}}
                """.formatted(message);
    }
}
