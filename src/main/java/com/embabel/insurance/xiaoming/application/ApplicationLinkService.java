package com.embabel.insurance.xiaoming.application;

import com.embabel.insurance.xiaoming.config.XiaomingApplicationProperties;
import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ApplicationLinkService {

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "conversationId", "customer", "customerId", "name", "phone", "idNumber",
            "health", "healthFacts", "message", "note", "redirect", "returnUrl", "url"
    );

    private final XiaomingApplicationProperties properties;
    private final XiaomingProductProperties product;
    private final Clock clock;
    private final Counter droppedParameters;

    @Autowired
    public ApplicationLinkService(
            XiaomingApplicationProperties properties,
            XiaomingProductProperties product,
            MeterRegistry meterRegistry
    ) {
        this(properties, product, Clock.systemUTC(),
                Counter.builder("xiaoming.application.parameters.dropped").register(meterRegistry));
    }

    ApplicationLinkService(
            XiaomingApplicationProperties properties,
            XiaomingProductProperties product,
            Clock clock,
            Counter droppedParameters
    ) {
        this.properties = properties;
        this.product = product;
        this.clock = clock;
        this.droppedParameters = droppedParameters;
    }

    public ApplicationLinkResult create(String productId, Map<String, String[]> requestParameters) {
        if (!product.productId().equals(productId)) {
            throw new ApplicationLinkException(ApplicationLinkException.Reason.INVALID_REQUEST,
                    "当前仅支持产品 36743。");
        }
        URI base = validatedBaseUri();
        Set<String> allowed = new HashSet<>(properties.allowedParams());
        List<String> query = new ArrayList<>();
        query.add("productId=" + encode(productId));
        int dropped = 0;

        for (Map.Entry<String, String[]> entry : requestParameters.entrySet()) {
            String key = entry.getKey();
            if ("productId".equals(key)) {
                String[] values = entry.getValue();
                if (values == null || values.length != 1 || !productId.equals(values[0])) {
                    throw new ApplicationLinkException(ApplicationLinkException.Reason.INVALID_REQUEST,
                            "产品参数格式无效。");
                }
                continue;
            }
            if (FORBIDDEN_KEYS.contains(key) || !allowed.contains(key)) {
                dropped++;
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length != 1 || !SAFE_VALUE.matcher(values[0]).matches()) {
                throw new ApplicationLinkException(ApplicationLinkException.Reason.INVALID_REQUEST,
                        "渠道参数格式无效。");
            }
            query.add(encode(key) + "=" + encode(values[0]));
        }
        if (dropped > 0) droppedParameters.increment(dropped);

        URI link;
        try {
            link = new URI(base.getScheme(), null, base.getHost(), base.getPort(),
                    base.getPath(), String.join("&", query), null);
        } catch (URISyntaxException exception) {
            throw notConfigured();
        }
        validateFinalUri(link);
        Instant expiresAt = clock.instant().plusSeconds(properties.ttlSeconds());
        return new ApplicationLinkResult(
                productId,
                normalizeEnvironment(product.environment()),
                link.toASCIIString(),
                normalizeHost(link.getHost()),
                expiresAt,
                "此链接仅用于打开正式投保页面，不代表已提交、已承保或投保成功。"
        );
    }

    private URI validatedBaseUri() {
        if (!properties.enabled() || properties.baseUrl().isBlank() || properties.allowedDomains().isEmpty()) {
            throw notConfigured();
        }
        URI uri;
        try {
            uri = URI.create(properties.baseUrl());
        } catch (IllegalArgumentException exception) {
            throw notConfigured();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                || uri.getPort() != -1 && uri.getPort() != 443) {
            throw notConfigured();
        }
        String host = normalizeHost(uri.getHost());
        boolean allowed = properties.allowedDomains().stream()
                .map(this::normalizeHost).anyMatch(host::equals);
        if (!allowed || isUnsafeProductionHost(host)) throw notConfigured();
        return uri;
    }

    private void validateFinalUri(URI uri) {
        String host = normalizeHost(uri.getHost());
        boolean allowed = properties.allowedDomains().stream()
                .map(this::normalizeHost).anyMatch(host::equals);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowed || uri.getUserInfo() != null
                || uri.getFragment() != null || isUnsafeProductionHost(host)) {
            throw notConfigured();
        }
    }

    private boolean isUnsafeProductionHost(String host) {
        if (!"production".equals(normalizeEnvironment(product.environment()))) return false;
        return host.equals("localhost") || host.endsWith(".localhost")
                || host.contains("uat") || host.contains("test") || host.contains("staging")
                || host.contains("dev") || host.endsWith(".example") || host.endsWith(".test");
    }

    private String normalizeEnvironment(String value) {
        return value == null ? "local" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeHost(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
        return IDN.toASCII(normalized).toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private ApplicationLinkException notConfigured() {
        return new ApplicationLinkException(ApplicationLinkException.Reason.NOT_CONFIGURED,
                "当前环境尚未配置可用的正式投保地址，请返回小明稍后重试。");
    }
}
