package com.embabel.insurance.xiaoming.occupation;

import java.util.List;

public record OccupationConfirmationResult(
        boolean matched,
        String occupationId,
        String name,
        Integer category,
        List<String> path,
        String knowledgeVersion,
        String occupationVersion,
        String notice
) {
    public OccupationConfirmationResult {
        path = List.copyOf(path);
    }
}
