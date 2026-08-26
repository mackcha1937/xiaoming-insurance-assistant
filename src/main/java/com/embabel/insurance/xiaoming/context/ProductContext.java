package com.embabel.insurance.xiaoming.context;

import java.util.List;

public record ProductContext(
        String productId,
        String name,
        String knowledgeVersion,
        String environment,
        boolean authenticationRequired,
        String accessMode,
        List<CapabilityDescriptor> capabilities
) {
    public ProductContext {
        capabilities = List.copyOf(capabilities);
    }
}
