package com.embabel.insurance.xiaoming.health;

import java.time.LocalDate;

public record HealthFactDraft(
        String factId,
        HealthFactType type,
        String code,
        String label,
        String value,
        Double numericValue,
        String unit,
        LocalDate date,
        HealthFactPolarity polarity,
        HealthSourceLocation sourceLocation,
        double confidence,
        boolean confirmed
) {
    public HealthFactDraft withConfirmed(boolean confirmed) {
        return new HealthFactDraft(
                factId, type, code, label, value, numericValue, unit, date,
                polarity, sourceLocation, confidence, confirmed
        );
    }
}
