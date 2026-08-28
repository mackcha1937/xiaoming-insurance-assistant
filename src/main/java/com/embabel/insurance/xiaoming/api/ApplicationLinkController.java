package com.embabel.insurance.xiaoming.api;

import com.embabel.insurance.xiaoming.application.ApplicationLinkException;
import com.embabel.insurance.xiaoming.application.ApplicationLinkResult;
import com.embabel.insurance.xiaoming.application.ApplicationLinkService;
import com.embabel.insurance.xiaoming.context.ProductContextService;
import com.embabel.insurance.xiaoming.contract.BusinessStatus;
import com.embabel.insurance.xiaoming.contract.XiaomingError;
import com.embabel.insurance.xiaoming.contract.XiaomingErrorCode;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.contract.XiaomingResponse;
import com.embabel.insurance.xiaoming.web.XiaomingRequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/xiaoming")
@Tag(name = "Xiaoming V2 Application", description = "Phase 07 安全正式投保外链")
public class ApplicationLinkController {

    private final ApplicationLinkService linkService;
    private final ProductContextService productContext;

    public ApplicationLinkController(ApplicationLinkService linkService, ProductContextService productContext) {
        this.linkService = linkService;
        this.productContext = productContext;
    }

    @GetMapping("/application-url")
    @Operation(summary = "生成当前环境的安全正式投保地址", security = {})
    public ResponseEntity<XiaomingResponse<ApplicationLinkResult>> applicationUrl(
            @RequestParam String productId,
            HttpServletRequest request
    ) {
        try {
            ApplicationLinkResult result = linkService.create(productId, request.getParameterMap());
            return ResponseEntity.ok(response(BusinessStatus.SUCCESS, "安全投保地址已生成。", result,
                    null, request));
        } catch (ApplicationLinkException exception) {
            XiaomingErrorCode code = exception.reason() == ApplicationLinkException.Reason.NOT_CONFIGURED
                    ? XiaomingErrorCode.APPLICATION_URL_NOT_CONFIGURED
                    : XiaomingErrorCode.VALIDATION_ERROR;
            XiaomingError error = new XiaomingError(code, exception.getMessage(), false, List.of());
            return ResponseEntity.status(code.getHttpStatus()).body(response(
                    BusinessStatus.ERROR, exception.getMessage(), null, error, request));
        }
    }

    private XiaomingResponse<ApplicationLinkResult> response(
            BusinessStatus status,
            String message,
            ApplicationLinkResult data,
            XiaomingError error,
            HttpServletRequest request
    ) {
        return new XiaomingResponse<>(status, XiaomingIntent.FORMAL_APPLICATION, message, data,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                productContext.currentKnowledgeVersion(), XiaomingRequestContext.traceId(request),
                XiaomingRequestContext.requestId(request), error);
    }
}
