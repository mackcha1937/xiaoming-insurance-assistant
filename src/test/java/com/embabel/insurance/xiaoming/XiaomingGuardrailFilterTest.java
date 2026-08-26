package com.embabel.insurance.xiaoming;

import com.embabel.insurance.guardrail.InputValidationFilter;
import com.embabel.insurance.guardrail.RateLimitFilter;
import com.embabel.insurance.guardrail.RateLimitingService;
import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.web.XiaomingErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XiaomingGuardrailFilterTest {

    private final XiaomingErrorWriter errorWriter = new XiaomingErrorWriter(
            new ObjectMapper(),
            new XiaomingProductProperties(
                    "36743",
                    "复星联合达尔文12号重大疾病保险（互联网）",
                    "v2.0-baseline",
                    "test"
            )
    );

    @Test
    void oversizedXiaomingRequestShouldUseUnifiedValidationError() throws Exception {
        InputValidationFilter filter = new InputValidationFilter(errorWriter);
        MockHttpServletRequest request = post("/api/v1/xiaoming/chat");
        request.setContent("x".repeat(5_001).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":\"ERROR\"")
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"knowledgeVersion\":\"v2.0-baseline\"");
    }

    @Test
    void rateLimitedXiaomingRequestShouldUseUnifiedRetryableError() throws Exception {
        RateLimitingService rateLimitingService = mock(RateLimitingService.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(rateLimitingService.tryConsume("anonymous", "/api/v1/xiaoming/chat")).thenReturn(false);
        RateLimitFilter filter = new RateLimitFilter(rateLimitingService, environment, errorWriter);
        MockHttpServletRequest request = post("/api/v1/xiaoming/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"retryable\":true");
    }

    @Test
    void oversizedLegacyRequestShouldKeepLegacyStatusAndShape() throws Exception {
        InputValidationFilter filter = new InputValidationFilter(errorWriter);
        MockHttpServletRequest request = post("/api/chat");
        request.setContent("x".repeat(5_001).getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"error\"");
        assertThat(response.getContentAsString()).doesNotContain("VALIDATION_ERROR");
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return request;
    }
}
