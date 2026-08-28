package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.checklist.ChecklistResult;
import com.embabel.insurance.xiaoming.checklist.ChecklistService;
import com.embabel.insurance.xiaoming.checklist.ChecklistServiceException;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
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
@RequestMapping("/api/v1/xiaoming/checklists")
@Tag(name = "Xiaoming V2 Checklist", description = "Phase 05 动态资料清单")
public class ChecklistController {

    private final ChecklistService checklistService;
    private final IdempotencyService idempotencyService;

    public ChecklistController(ChecklistService checklistService, IdempotencyService idempotencyService) {
        this.checklistService = checklistService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/generate")
    @Operation(summary = "按当前版本和非敏感条件生成动态资料清单", security = {})
    public ResponseEntity<XiaomingResponse<ChecklistResult>> generate(
            @Valid @RequestBody ChecklistGenerateRequest generateRequest,
            @RequestHeader(value = XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            HttpServletRequest request
    ) {
        if (generateRequest.hasUnknownFields()) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "资料清单请求包含未允许字段。"
            );
        }
        if (!idempotencyService.isValidKey(idempotencyKey)) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key 缺失或格式无效。",
                    false,
                    List.of(new XiaomingError.FieldViolation("Idempotency-Key", "必须为 16—128 位安全字符"))
            );
        }

        String fingerprint = idempotencyService.fingerprint(generateRequest.fingerprintPayload());
        try {
            IdempotencyService.IdempotencyResult<ChecklistResult> result = idempotencyService.execute(
                    "POST:/api/v1/xiaoming/checklists/generate",
                    idempotencyKey,
                    fingerprint,
                    () -> {
                        ChecklistResult checklist = checklistService.generate(generateRequest);
                        return new IdempotencyService.StoredResponse<>(HttpStatus.OK, response(
                                BusinessStatus.SUCCESS,
                                "已按当前资料版本生成准备清单。",
                                checklist,
                                checklist.knowledgeVersion(),
                                request,
                                null
                        ));
                    }
            );
            if (result.conflict()) {
                throw new XiaomingApiException(
                        XiaomingErrorCode.VALIDATION_ERROR,
                        "同一 Idempotency-Key 不得用于不同请求。"
                );
            }
            XiaomingResponse<ChecklistResult> body = result.response().body();
            return ResponseEntity.status(result.response().status())
                    .header(XiaomingRequestContext.TRACE_ID_HEADER, body.traceId())
                    .header(XiaomingRequestContext.REQUEST_ID_HEADER, body.requestId())
                    .header(XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .header(XiaomingFoundationController.IDEMPOTENCY_REPLAYED_HEADER,
                            Boolean.toString(result.replayed()))
                    .body(body);
        } catch (ChecklistServiceException exception) {
            return checklistFailure(exception, request);
        }
    }

    private XiaomingResponse<ChecklistResult> response(
            BusinessStatus status,
            String message,
            ChecklistResult data,
            String knowledgeVersion,
            HttpServletRequest request,
            XiaomingError error
    ) {
        return new XiaomingResponse<>(
                status,
                XiaomingIntent.MATERIAL_CHECKLIST,
                message,
                data,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                knowledgeVersion,
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request),
                error
        );
    }

    private ResponseEntity<XiaomingResponse<ChecklistResult>> checklistFailure(
            ChecklistServiceException exception,
            HttpServletRequest request
    ) {
        if (exception.reason() == ChecklistServiceException.Reason.VERSION_CONFLICT) {
            XiaomingError error = new XiaomingError(
                    XiaomingErrorCode.KNOWLEDGE_CONFLICT,
                    exception.getMessage(),
                    false,
                    List.of()
            );
            return ResponseEntity.ok(response(
                    BusinessStatus.CONFLICT,
                    exception.getMessage(),
                    null,
                    exception.currentKnowledgeVersion(),
                    request,
                    error
            ));
        }
        XiaomingError error = new XiaomingError(
                XiaomingErrorCode.DEPENDENCY_UNAVAILABLE,
                exception.getMessage(),
                true,
                List.of()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response(
                BusinessStatus.ERROR,
                exception.getMessage(),
                null,
                exception.currentKnowledgeVersion(),
                request,
                error
        ));
    }
}
