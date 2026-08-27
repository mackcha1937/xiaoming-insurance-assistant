package com.embabel.insurance.xiaoming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "embabel.agent.shell.interactive.enabled=false",
        "embabel.agent.shell.web-application-type=servlet",
        "xiaoming.knowledge-import.enabled=true",
        "xiaoming.knowledge-import.source-directory=docs/source-materials/product-36743/raw",
        "xiaoming.knowledge-import.spreadsheet-scan-mode=STRUCTURE_ONLY"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "xiaoming.startupImportTest", matches = "true")
class KnowledgeImportStartupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explicitStartupImportPublishesTheConfirmedVersionBeforeServingStatus() throws Exception {
        mockMvc.perform(get("/api/v1/xiaoming/knowledge/current")
                        .header("X-Request-Id", "startup-import-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value("36743"))
                .andExpect(jsonPath("$.data.knowledgeVersion").value("knowledge-36743-2026-08-27"))
                .andExpect(jsonPath("$.data.documentCount").value(21))
                .andExpect(jsonPath("$.data.formalSourcesLoaded").value(true))
                .andExpect(jsonPath("$.knowledgeVersion").value("knowledge-36743-2026-08-27"));
    }
}
