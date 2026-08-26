package com.embabel.insurance.xiaoming.evidence;

import com.embabel.insurance.xiaoming.contract.XiaomingEvidence;
import com.embabel.insurance.xiaoming.retrieval.RetrievalMode;

import java.util.List;

public record EvidenceDecision(
        EvidenceGateStatus status,
        String reason,
        List<XiaomingEvidence> evidence,
        List<String> ruleIds,
        RetrievalMode retrievalMode,
        boolean degraded
) {
    public EvidenceDecision {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }
}
