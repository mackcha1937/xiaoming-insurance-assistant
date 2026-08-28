package com.embabel.insurance.xiaoming.health;

public record HealthSourceLocation(
        String sourceId,
        String sourceLabel,
        Integer page,
        Integer line,
        String excerpt
) {
}
