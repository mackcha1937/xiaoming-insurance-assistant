package com.embabel.insurance.guardrail;

import com.embabel.insurance.xiaoming.web.XiaomingErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InputValidationFilterTest {

    @Test
    void multipartPassesThroughWithoutConsumingOrRewrappingTheBoundaryStream() throws Exception {
        XiaomingErrorWriter writer = mock(XiaomingErrorWriter.class);
        FilterChain chain = mock(FilterChain.class);
        InputValidationFilter filter = new InputValidationFilter(writer);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/xiaoming/health/extract"
        );
        request.setContentType("multipart/form-data; boundary=phase06-boundary");
        byte[] body = ("--phase06-boundary\r\n" + "x".repeat(6_000)).getBytes(StandardCharsets.UTF_8);
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(writer);
        assertArrayEquals(body, request.getInputStream().readAllBytes());
    }
}
