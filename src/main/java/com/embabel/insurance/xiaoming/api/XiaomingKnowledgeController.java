package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersion;
import com.embabel.insurance.xiaoming.knowledge.KnowledgeVersionRegistry;
import com.embabel.insurance.xiaoming.web.XiaomingRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/xiaoming/knowledge")
@Tag(name = "Xiaoming V2 Knowledge", description = "Phase 02 知识版本与证据底座")
public class XiaomingKnowledgeController {

    private final KnowledgeVersionRegistry registry;
    private final XiaomingProductProperties properties;

    public XiaomingKnowledgeController(
            KnowledgeVersionRegistry registry,
            XiaomingProductProperties properties
    ) {
        this.registry = registry;
        this.properties = properties;
    }

    @GetMapping("/current")
    @Operation(summary = "读取当前已发布知识版本状态", security = {})
    public XiaomingResponse<KnowledgeVersionSummary> current(HttpServletRequest request) {
        KnowledgeVersion current = registry.current();
        KnowledgeVersionSummary summary = new KnowledgeVersionSummary(
                current.productId(),
                current.versionId(),
                current.status(),
                current.rulesVersion(),
                current.occupationVersion(),
                current.publishedAt(),
                current.documents().size(),
                !current.documents().isEmpty()
        );
        return XiaomingResponse.success(
                summary.formalSourcesLoaded()
                        ? "当前正式知识版本已加载。"
                        : "知识底座已就绪，正式产品资料尚未导入。",
                summary,
                properties.currentKnowledgeVersion(),
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request)
        );
    }
}
