package com.embabel.insurance.xiaoming.occupation;

import java.util.List;

public record OccupationCandidate(
        String occupationId,
        String name,
        List<String> path
) {
    public OccupationCandidate {
        path = List.copyOf(path);
    }
}
