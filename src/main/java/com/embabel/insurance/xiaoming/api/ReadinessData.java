package com.embabel.insurance.xiaoming.api;

import java.util.Map;

public record ReadinessData(String status, Map<String, String> dependencies) {
    public ReadinessData {
        dependencies = Map.copyOf(dependencies);
    }
}
