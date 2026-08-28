package com.embabel.insurance.xiaoming.health;

public record HealthFileResult(
        String fileId,
        String label,
        HealthFileStatus status,
        String code,
        String message,
        int factCount
) {
}
