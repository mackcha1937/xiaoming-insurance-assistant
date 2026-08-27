package com.embabel.insurance.xiaoming.guardrail;

import com.embabel.insurance.xiaoming.contract.XiaomingIntent;

import java.util.List;

public record IntentGuardDecision(
        IntentGuardStatus status,
        List<XiaomingIntent> allowedIntents,
        List<XiaomingIntent> unavailableIntents,
        String refusalReason
) {
    public IntentGuardDecision {
        allowedIntents = allowedIntents == null ? List.of() : List.copyOf(allowedIntents);
        unavailableIntents = unavailableIntents == null ? List.of() : List.copyOf(unavailableIntents);
        refusalReason = refusalReason == null ? "" : refusalReason;
    }
}
