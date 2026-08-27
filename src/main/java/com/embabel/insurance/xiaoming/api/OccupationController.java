package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.MissingField;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.occupation.OccupationConfirmationResult;
import com.embabel.insurance.xiaoming.occupation.OccupationSearchResult;
import com.embabel.insurance.xiaoming.occupation.OccupationService;
import com.embabel.insurance.xiaoming.occupation.OccupationServiceException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/xiaoming/occupations")
@Tag(name = "Xiaoming V2 Occupation", description = "Phase 04 职业候选与 occupationId 人工确认")
public class OccupationController {

    private static final Pattern SAFE_QUERY_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");

    private final OccupationService occupationService;
    private final IdempotencyService idempotencyService;

    public OccupationController(OccupationService occupationService, IdempotencyService idempotencyService) {
        this.occupationService = occupationService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/search")
    @Operation(summary = "搜索最多五项职业候选；候选阶段不返回职业类别", security = {})
    public ResponseEntity<XiaomingResponse<OccupationSearchResult>> search(
            @RequestParam String productId,
            @RequestParam(name = "q") String query,
            @RequestParam String queryToken,
            @RequestParam(required = false) String knowledgeVersionHint,
            HttpServletRequest request
    ) {
        validateSearch(productId, query, queryToken, knowledgeVersionHint);
        try {
            OccupationSearchResult result = occupationService.search(
                    productId,
                    query,
                    knowledgeVersionHint,
                    queryToken
            );
            BusinessStatus status = result.requiresMoreDetail() ? BusinessStatus.NEED_INPUT : BusinessStatus.SUCCESS;
            List<MissingField> missingFields = result.requiresMoreDetail()
                    ? List.of(new MissingField(
                            "occupationDescription",
                            "补充具体工作内容",
                            "text",
                            true,
                            List.of()
                    ))
                    : List.of();
            XiaomingResponse<OccupationSearchResult> body = response(
                    status,
                    result.prompt(),
                    result,
                    missingFields,
                    result.knowledgeVersion(),
                    request,
                    null
            );
            return ResponseEntity.ok(body);
        } catch (OccupationServiceException exception) {
            return occupationFailure(exception, request);
        }
    }

    @PostMapping("/confirm")
    @Operation(summary = "只按当前版本中的 occupationId 精确确认职业", security = {})
    public ResponseEntity<XiaomingResponse<OccupationConfirmationResult>> confirm(
            @Valid @RequestBody OccupationConfirmRequest confirmRequest,
            @RequestHeader(value = XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            HttpServletRequest request
    ) {
        if (confirmRequest.hasUnknownFields()) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "确认职业请求只接受 productId、occupationId 和 knowledgeVersion。"
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

        String fingerprint = idempotencyService.fingerprint(confirmRequest.fingerprintPayload());
        try {
            IdempotencyService.IdempotencyResult<OccupationConfirmationResult> result = idempotencyService.execute(
                    "POST:/api/v1/xiaoming/occupations/confirm",
                    idempotencyKey,
                    fingerprint,
                    () -> {
                        OccupationConfirmationResult confirmation = occupationService.confirm(
                                confirmRequest.getProductId(),
                                confirmRequest.getOccupationId(),
                                confirmRequest.getKnowledgeVersion()
                        );
                        String message = confirmation.matched()
                                ? "已按当前职业表精确确认。"
                                : "当前职业表未找到该 occupationId，请重新查询。";
                        return new IdempotencyService.StoredResponse<>(
                                HttpStatus.OK,
                                response(
                                        BusinessStatus.SUCCESS,
                                        message,
                                        confirmation,
                                        List.of(),
                                        confirmation.knowledgeVersion(),
                                        request,
                                        null
                                )
                        );
                    }
            );
            if (result.conflict()) {
                throw new XiaomingApiException(
                        XiaomingErrorCode.VALIDATION_ERROR,
                        "同一 Idempotency-Key 不得用于不同请求。"
                );
            }
            XiaomingResponse<OccupationConfirmationResult> body = result.response().body();
            return ResponseEntity.status(result.response().status())
                    .header(XiaomingRequestContext.TRACE_ID_HEADER, body.traceId())
                    .header(XiaomingRequestContext.REQUEST_ID_HEADER, body.requestId())
                    .header(XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .header(XiaomingFoundationController.IDEMPOTENCY_REPLAYED_HEADER, Boolean.toString(result.replayed()))
                    .body(body);
        } catch (OccupationServiceException exception) {
            return occupationFailure(exception, request);
        }
    }

    private void validateSearch(
            String productId,
            String query,
            String queryToken,
            String knowledgeVersionHint
    ) {
        if (!"36743".equals(productId)) {
            throw validation("productId", "当前仅支持产品 36743");
        }
        if (query == null || query.isBlank() || query.length() > 200) {
            throw validation("q", "职业描述必须为 1—200 个字符");
        }
        if (queryToken == null || !SAFE_QUERY_TOKEN.matcher(queryToken).matches()) {
            throw validation("queryToken", "queryToken 格式无效");
        }
        if (knowledgeVersionHint != null && knowledgeVersionHint.length() > 64) {
            throw validation("knowledgeVersionHint", "knowledgeVersionHint 不能超过 64 个字符");
        }
    }

    private XiaomingApiException validation(String field, String message) {
        return new XiaomingApiException(
                XiaomingErrorCode.VALIDATION_ERROR,
                "职业查询参数不符合契约。",
                false,
                List.of(new XiaomingError.FieldViolation(field, message))
        );
    }

    private <T> XiaomingResponse<T> response(
            BusinessStatus status,
            String message,
            T data,
            List<MissingField> missingFields,
            String knowledgeVersion,
            HttpServletRequest request,
            XiaomingError error
    ) {
        return new XiaomingResponse<>(
                status,
                XiaomingIntent.OCCUPATION_QUERY,
                message,
                data,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                missingFields,
                knowledgeVersion,
                XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request),
                error
        );
    }

    private <T> ResponseEntity<XiaomingResponse<T>> occupationFailure(
            OccupationServiceException exception,
            HttpServletRequest request
    ) {
        if (exception.reason() == OccupationServiceException.Reason.VERSION_CONFLICT) {
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
                    List.of(),
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
                List.of(),
                exception.currentKnowledgeVersion(),
                request,
                error
        ));
    }
}
