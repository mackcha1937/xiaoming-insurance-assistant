package com.embabel.insurance.xiaoming.application;

import java.time.Instant;

public record ApplicationLinkResult(
        String productId,
        String environment,
        String url,
        String allowedDomain,
        Instant expiresAt,
        String notice
) {
}
