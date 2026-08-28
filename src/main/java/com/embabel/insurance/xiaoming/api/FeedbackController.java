package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.context.ProductContextService;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.feedback.FeedbackResult;
import com.embabel.insurance.xiaoming.feedback.FeedbackService;
import com.embabel.insurance.xiaoming.service.IdempotencyService;
import com.embabel.insurance.xiaoming.web.XiaomingApiException;
import com.embabel.insurance.xiaoming.web.XiaomingRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/xiaoming")
@Tag(name = "Xiaoming V2 Feedback", description = "Phase 07 顾问主动脱敏反馈")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final IdempotencyService idempotencyService;
    private final ProductContextService productContext;

    public FeedbackController(FeedbackService feedbackService, IdempotencyService idempotencyService,
                              ProductContextService productContext) {
        this.feedbackService = feedbackService;
        this.idempotencyService = idempotencyService;
        this.productContext = productContext;
    }

    @PostMapping("/feedback")
    @Operation(summary = "提交顾问主动选择的脱敏回答反馈", security = {})
    public ResponseEntity<XiaomingResponse<FeedbackResult>> submit(
            @Valid @RequestBody FeedbackRequest feedbackRequest,
            @RequestHeader(value = XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            HttpServletRequest request
    ) {
        if (feedbackRequest.hasUnknownFields() || !idempotencyService.isValidKey(idempotencyKey)) {
            throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR,
                    "反馈请求字段或 Idempotency-Key 无效。");
        }
        String fingerprint = idempotencyService.fingerprint(feedbackRequest.fingerprintPayload());
        try {
            IdempotencyService.IdempotencyResult<FeedbackResult> result = idempotencyService.execute(
                    "POST:/api/v1/xiaoming/feedback", idempotencyKey, fingerprint,
                    () -> new IdempotencyService.StoredResponse<>(HttpStatus.OK,
                            XiaomingResponse.success("反馈已安全接收。", feedbackService.submit(feedbackRequest),
                                    productContext.currentKnowledgeVersion(), XiaomingRequestContext.traceId(request),
                                    XiaomingRequestContext.requestId(request)))
            );
            if (result.conflict()) throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR,
                    "同一 Idempotency-Key 不得用于不同反馈。");
            XiaomingResponse<FeedbackResult> body = result.response().body();
            return ResponseEntity.status(result.response().status())
                    .header(XiaomingRequestContext.TRACE_ID_HEADER, body.traceId())
                    .header(XiaomingRequestContext.REQUEST_ID_HEADER, body.requestId())
                    .header(XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .header(XiaomingFoundationController.IDEMPOTENCY_REPLAYED_HEADER,
                            Boolean.toString(result.replayed())).body(body);
        } catch (IllegalArgumentException exception) {
            throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new XiaomingApiException(XiaomingErrorCode.DEPENDENCY_UNAVAILABLE,
                    "反馈服务暂时不可用。", true, List.of());
        }
    }
}
