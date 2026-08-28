package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.health.HealthExtractCommand;
import com.embabel.insurance.xiaoming.health.HealthExtractionResult;
import com.embabel.insurance.xiaoming.health.HealthExtractionService;
import com.embabel.insurance.xiaoming.health.HealthFileStatus;
import com.embabel.insurance.xiaoming.health.HealthMatchResult;
import com.embabel.insurance.xiaoming.health.HealthMatchService;
import com.embabel.insurance.xiaoming.health.HealthMatchState;
import com.embabel.insurance.xiaoming.health.HealthServiceException;
import com.embabel.insurance.xiaoming.service.IdempotencyService;
import com.embabel.insurance.xiaoming.web.XiaomingApiException;
import com.embabel.insurance.xiaoming.web.XiaomingRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/xiaoming/health")
@Tag(name = "Xiaoming V2 Health", description = "Phase 06 健康事实提取、人工确认与三态匹配")
public class HealthController {

    private static final Pattern SAFE_CONVERSATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}");
    private static final Set<String> EXTRACT_PARTS = Set.of(
            "productId", "conversationId", "knowledgeVersion", "authorized", "text", "files"
    );

    private final HealthExtractionService extractionService;
    private final HealthMatchService matchService;
    private final IdempotencyService idempotencyService;

    public HealthController(
            HealthExtractionService extractionService,
            HealthMatchService matchService,
            IdempotencyService idempotencyService
    ) {
        this.extractionService = extractionService;
        this.matchService = matchService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "在授权后从文字或文件提取健康事实草稿", security = {})
    public ResponseEntity<XiaomingResponse<HealthExtractionResult>> extract(
            @RequestParam String productId,
            @RequestParam String conversationId,
            @RequestParam String knowledgeVersion,
            @RequestParam Boolean authorized,
            @RequestPart(value = "text", required = false) String text,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader(value = XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            HttpServletRequest request
    ) {
        validateExtractEnvelope(productId, conversationId, knowledgeVersion, authorized, idempotencyKey, request);
        List<MultipartFile> safeFiles = files == null ? List.of() : List.copyOf(files);
        HealthExtractCommand command = new HealthExtractCommand(
                productId, conversationId, knowledgeVersion, Boolean.TRUE.equals(authorized), text, safeFiles
        );
        String fingerprint = fingerprint(command);
        try {
            IdempotencyService.IdempotencyResult<HealthExtractionResult> result = idempotencyService.execute(
                    "POST:/api/v1/xiaoming/health/extract",
                    idempotencyKey,
                    fingerprint,
                    () -> {
                        HealthExtractionResult extraction = extractionService.extract(command);
                        boolean allFailed = !extraction.files().isEmpty()
                                && extraction.files().stream().allMatch(file -> file.status() == HealthFileStatus.FAILED);
                        BusinessStatus status = allFailed && extraction.facts().isEmpty()
                                ? BusinessStatus.NEED_INPUT
                                : BusinessStatus.SUCCESS;
                        String message = status == BusinessStatus.SUCCESS
                                ? "健康事实草稿已生成，请逐项确认。"
                                : "文件未能提取事实，请按提示改用手工文字或重新选择。";
                        return new IdempotencyService.StoredResponse<>(HttpStatus.OK, response(
                                status, message, extraction, extraction.knowledgeVersion(), request,
                                List.of(), null
                        ));
                    }
            );
            if (result.conflict()) throw idempotencyConflict();
            return withHeaders(result, idempotencyKey);
        } catch (HealthServiceException exception) {
            return failure(exception, request);
        }
    }

    @PostMapping("/match")
    @Operation(summary = "只使用顾问已确认事实执行健康告知三态匹配", security = {})
    public ResponseEntity<XiaomingResponse<HealthMatchResult>> match(
            @Valid @RequestBody HealthMatchRequest matchRequest,
            @RequestHeader(value = XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            HttpServletRequest request
    ) {
        if (matchRequest.hasUnknownFields()) {
            throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR, "健康匹配请求包含未允许字段。");
        }
        validateKey(idempotencyKey);
        String fingerprint = idempotencyService.fingerprint(matchRequest.fingerprintPayload());
        try {
            IdempotencyService.IdempotencyResult<HealthMatchResult> result = idempotencyService.execute(
                    "POST:/api/v1/xiaoming/health/match",
                    idempotencyKey,
                    fingerprint,
                    () -> {
                        HealthMatchResult match = matchService.match(matchRequest);
                        BusinessStatus status = match.state() == HealthMatchState.INSUFFICIENT
                                ? BusinessStatus.NEED_INPUT
                                : BusinessStatus.SUCCESS;
                        List<XiaomingEvidence> evidence = match.questions().stream()
                                .map(question -> question.evidence())
                                .distinct()
                                .toList();
                        return new IdempotencyService.StoredResponse<>(HttpStatus.OK, response(
                                status, match.notice(), match, match.knowledgeVersion(), request, evidence, null
                        ));
                    }
            );
            if (result.conflict()) throw idempotencyConflict();
            return withHeaders(result, idempotencyKey);
        } catch (HealthServiceException exception) {
            return failure(exception, request);
        }
    }

    private void validateExtractEnvelope(
            String productId,
            String conversationId,
            String knowledgeVersion,
            Boolean authorized,
            String idempotencyKey,
            HttpServletRequest request
    ) {
        List<XiaomingError.FieldViolation> fields = new ArrayList<>();
        if (!"36743".equals(productId)) fields.add(new XiaomingError.FieldViolation("productId", "当前仅支持产品 36743"));
        if (conversationId == null || !SAFE_CONVERSATION.matcher(conversationId).matches()) {
            fields.add(new XiaomingError.FieldViolation("conversationId", "conversationId 格式无效"));
        }
        if (knowledgeVersion == null || knowledgeVersion.isBlank() || knowledgeVersion.length() > 64) {
            fields.add(new XiaomingError.FieldViolation("knowledgeVersion", "knowledgeVersion 格式无效"));
        }
        if (!Boolean.TRUE.equals(authorized)) fields.add(new XiaomingError.FieldViolation("authorized", "必须先明确授权"));
        if (hasUnknownParts(request)) fields.add(new XiaomingError.FieldViolation("request", "请求包含未知字段"));
        if (!fields.isEmpty()) {
            throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR,
                    "健康提取请求不符合契约。", false, fields);
        }
        validateKey(idempotencyKey);
    }

    private boolean hasUnknownParts(HttpServletRequest request) {
        for (String parameterName : request.getParameterMap().keySet()) {
            if (!EXTRACT_PARTS.contains(parameterName)) return true;
        }
        try {
            for (Part part : request.getParts()) {
                if (!EXTRACT_PARTS.contains(part.getName())) return true;
            }
            return false;
        } catch (IOException | ServletException exception) {
            throw new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR, "无法解析上传请求。", true, List.of());
        }
    }

    private void validateKey(String idempotencyKey) {
        if (!idempotencyService.isValidKey(idempotencyKey)) {
            throw new XiaomingApiException(
                    XiaomingErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key 缺失或格式无效。",
                    false,
                    List.of(new XiaomingError.FieldViolation("Idempotency-Key", "必须为 16—128 位安全字符"))
            );
        }
    }

    private String fingerprint(HealthExtractCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productId", command.productId());
        payload.put("conversationId", command.conversationId());
        payload.put("knowledgeVersion", command.knowledgeVersion());
        payload.put("authorized", command.authorized());
        payload.put("textHash", sha256(command.text() == null ? new byte[0] : command.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<String> fileHashes = new ArrayList<>();
        for (MultipartFile file : command.files()) {
            try (InputStream input = file.getInputStream()) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
                fileHashes.add(file.getSize() + ":" + safeContentType(file) + ":" + HexFormat.of().formatHex(digest.digest()));
            } catch (IOException | NoSuchAlgorithmException exception) {
                throw new XiaomingApiException(XiaomingErrorCode.FILE_UNSUPPORTED, "无法读取上传文件。", true, List.of());
            }
        }
        payload.put("files", fileHashes);
        return idempotencyService.fingerprint(payload);
    }

    private String safeContentType(MultipartFile file) {
        return file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private XiaomingApiException idempotencyConflict() {
        return new XiaomingApiException(XiaomingErrorCode.VALIDATION_ERROR,
                "同一 Idempotency-Key 不得用于不同请求。");
    }

    private <T> ResponseEntity<XiaomingResponse<T>> withHeaders(
            IdempotencyService.IdempotencyResult<T> result,
            String idempotencyKey
    ) {
        XiaomingResponse<T> body = result.response().body();
        return ResponseEntity.status(result.response().status())
                .header(XiaomingRequestContext.TRACE_ID_HEADER, body.traceId())
                .header(XiaomingRequestContext.REQUEST_ID_HEADER, body.requestId())
                .header(XiaomingFoundationController.IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .header(XiaomingFoundationController.IDEMPOTENCY_REPLAYED_HEADER, Boolean.toString(result.replayed()))
                .body(body);
    }

    private <T> XiaomingResponse<T> response(
            BusinessStatus status,
            String message,
            T data,
            String version,
            HttpServletRequest request,
            List<XiaomingEvidence> evidence,
            XiaomingError error
    ) {
        return new XiaomingResponse<>(
                status, XiaomingIntent.HEALTH_DISCLOSURE, message, data,
                List.of(), evidence, List.of(), List.of(), List.of(), version,
                XiaomingRequestContext.traceId(request), XiaomingRequestContext.requestId(request), error
        );
    }

    private <T> ResponseEntity<XiaomingResponse<T>> failure(
            HealthServiceException exception,
            HttpServletRequest request
    ) {
        XiaomingErrorCode code;
        BusinessStatus status;
        HttpStatus httpStatus;
        boolean retryable = false;
        switch (exception.reason()) {
            case VERSION_CONFLICT -> {
                code = XiaomingErrorCode.KNOWLEDGE_CONFLICT;
                status = BusinessStatus.CONFLICT;
                httpStatus = HttpStatus.OK;
            }
            case SNAPSHOT_UNAVAILABLE -> {
                code = XiaomingErrorCode.DEPENDENCY_UNAVAILABLE;
                status = BusinessStatus.ERROR;
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
                retryable = true;
            }
            case FILE_UNSUPPORTED -> {
                code = XiaomingErrorCode.FILE_UNSUPPORTED;
                status = BusinessStatus.ERROR;
                httpStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            }
            default -> {
                code = XiaomingErrorCode.VALIDATION_ERROR;
                status = BusinessStatus.ERROR;
                httpStatus = HttpStatus.BAD_REQUEST;
            }
        }
        XiaomingError error = new XiaomingError(code, exception.getMessage(), retryable, List.of());
        XiaomingResponse<T> body = response(
                status, exception.getMessage(), null, exception.currentKnowledgeVersion(), request, List.of(), error
        );
        return ResponseEntity.status(httpStatus).body(body);
    }
}
