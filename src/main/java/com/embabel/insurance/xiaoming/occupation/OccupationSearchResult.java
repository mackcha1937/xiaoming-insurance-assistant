package com.embabel.insurance.xiaoming.occupation;

import java.util.List;

public record OccupationSearchResult(
        String queryToken,
        List<OccupationCandidate> candidates,
        boolean requiresMoreDetail,
        String prompt,
        String knowledgeVersion,
        String occupationVersion
) {
    public OccupationSearchResult {
        candidates = List.copyOf(candidates);
    }
}
