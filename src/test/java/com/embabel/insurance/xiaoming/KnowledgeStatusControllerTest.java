package com.embabel.insurance.xiaoming;

import com.embabel.insurance.xiaoming.api.KnowledgeVersionSummary;
import com.embabel.insurance.xiaoming.api.XiaomingKnowledgeController;
import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KnowledgeStatusControllerTest {

    @Test
    void exposesPublishedVersionWithoutPretendingFormalSourcesAreLoaded() {
        XiaomingProductProperties properties = new XiaomingProductProperties(
                "36743",
                "复星联合达尔文12号重大疾病保险（互联网）",
                "v2.0-baseline",
                "test"
        );
        KnowledgeVersionRegistry registry = new KnowledgeVersionRegistry(
                properties,
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );
        XiaomingKnowledgeController controller = new XiaomingKnowledgeController(registry, properties);

        XiaomingResponse<KnowledgeVersionSummary> response = controller.current(
                new MockHttpServletRequest()
        );

        assertEquals("SUCCESS", response.status().name());
        assertEquals("36743", response.data().productId());
        assertEquals("v2.0-baseline", response.data().knowledgeVersion());
        assertEquals(KnowledgeVersionStatus.PUBLISHED, response.data().status());
        assertEquals(0, response.data().documentCount());
        assertFalse(response.data().formalSourcesLoaded());
        assertNotNull(response.traceId());
        assertNotNull(response.requestId());
        assertEquals("知识底座已就绪，正式产品资料尚未导入。", response.message());
    }
}
