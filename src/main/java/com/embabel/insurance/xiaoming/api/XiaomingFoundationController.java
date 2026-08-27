package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.conversation.ConversationResult;
import com.embabel.insurance.xiaoming.conversation.XiaomingConversationService;
import com.embabel.insurance.xiaoming.context.ProductContext;
import com.embabel.insurance.xiaoming.context.ProductContextService;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.service.FoundationReadinessService;
import com.embabel.insurance.xiaoming.service.IdempotencyService;
import com.embabel.insurance.xiaoming.web.XiaomingApiException;
import com.embabel.insurance.xiaoming.web.XiaomingRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/xiaoming")
@Tag(name = "Xiaoming V2 Conversation", description = "Phase 03 限定域对话、规则与证据门禁")
public class XiaomingFoundationController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private final ProductContextService productContextService;
    private final FoundationReadinessService readinessService;
    private final IdempotencyService idempotencyService;
    private final XiaomingProductProperties properties;
    private final XiaomingConversationService conversationService;

    public XiaomingFoundationController(
            ProductContextService productContextService,
            FoundationReadinessService readinessService,
            IdempotencyService idempotencyService,
            XiaomingProductProperties properties,
            XiaomingConversationService conversationService
    ) {
        this.productContextService = productContextService;
        this.readinessService = readinessService;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.conversationService = conversationService;
    }

    @GetMapping("/product-context")
    @Operation(summary = "读取当前固定产品上下文", security = {})
    public XiaomingResponse<ProductContext> productContext(HttpServletRequest request) {
        return XiaomingResponse.success(
                "产品上下文已就绪。",
                productContextService.current(),
                productContextService.currentKnowledgeVersion(),
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request)
        );
    }

    @GetMapping("/health/ready")
    @Operation(summary = "检查 API 与数据库 readiness", security = {})
    public ResponseEntity<XiaomingResponse<ReadinessData>> readiness(HttpServletRequest request) {
        ReadinessData readiness = readinessService.check();
        if (!"UP".equals(readiness.status())) {
            XiaomingResponse<ReadinessData> body = XiaomingResponse.error(
                    "服务依赖尚未就绪。",
                    new XiaomingError(
                            XiaomingErrorCode.DEPENDENCY_UNAVAILABLE,
                            "服务依赖尚未就绪。",
                            true,
                            List.of()
                    ),
                    properties.currentKnowledgeVersion(),
                    XiaomingRequestContext.traceId(request),
                    XiaomingRequestContext.requestId(request)
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(XiaomingResponse.success(
                "服务已就绪。",
                readiness,
                properties.currentKnowledgeVersion(),
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request)
        ));
    }

    @PostMapping("/chat")
    @Operation(summary = "限定域对话：白名单、结构化规则和正式证据门禁", security = {})
    public ResponseEntity<XiaomingResponse<Map<String, Object>>> chat(
            @Valid @RequestBody ChatRequest chatRequest,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            HttpServletRequest request
    ) {
        validateChatRequest(chatRequest, idempotencyKey);
        String fingerprint = idempotencyService.fingerprint(chatRequest.fingerprintPayload());
        IdempotencyService.IdempotencyResult<Map<String, Object>> result = idempotencyService.execute(
                "POST:/api/v1/xiaoming/chat",
                idempotencyKey,
                fingerprint,
                () -> {
                    ConversationResult conversation = conversationService.answer(
                            chatRequest,
                            XiaomingRequestContext.traceId(request),
                            XiaomingRequestContext.requestId(request)
                    );
                    return new IdempotencyService.StoredResponse<>(
                            conversation.httpStatus(),
                            conversation.response()
                    );
                }
        );

        if (result.conflict()) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "同一 Idempotency-Key 不得用于不同请求。"
            );
        }

        XiaomingResponse<Map<String, Object>> body = result.response().body();
        return ResponseEntity.status(result.response().status())
                .header(XiaomingRequestContext.TRACE_ID_HEADER, body.traceId())
                .header(XiaomingRequestContext.REQUEST_ID_HEADER, body.requestId())
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .header(IDEMPOTENCY_REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(body);
    }

    private void validateChatRequest(ChatRequest request, String idempotencyKey) {
        if (request.hasUnknownFields()) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "请求包含未知字段。",
                    false,
                    List.of(new XiaomingError.FieldViolation("request", "请求包含未知字段"))
            );
        }
        if (!idempotencyService.isValidKey(idempotencyKey)) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key 缺失或格式无效。",
                    false,
                    List.of(new XiaomingError.FieldViolation(
                            "Idempotency-Key",
                            "必须为 16—128 位安全字符"
                    ))
            );
        }
    }
}
