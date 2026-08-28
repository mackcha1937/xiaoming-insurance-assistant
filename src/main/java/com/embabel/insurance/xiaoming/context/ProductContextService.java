package com.embabel.insurance.xiaoming.context;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import com.embabel.insurance.xiaoming.knowledge.importer.KnowledgeSnapshotStore;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class ProductContextService {

    private static final Set<XiaomingIntent> PHASE_07_CAPABILITIES = Set.of(XiaomingIntent.values());

    private final XiaomingProductProperties properties;
    private final KnowledgeSnapshotStore snapshotStore;

    public ProductContextService(
            XiaomingProductProperties properties,
            KnowledgeSnapshotStore snapshotStore
    ) {
        this.properties = properties;
        this.snapshotStore = snapshotStore;
    }

    public ProductContext current() {
        List<CapabilityDescriptor> capabilities = Arrays.stream(XiaomingIntent.values())
                .map(intent -> new CapabilityDescriptor(
                        intent,
                        intent.getLabel(),
                        PHASE_07_CAPABILITIES.contains(intent)
                ))
                .toList();

        return new ProductContext(
                properties.productId(),
                properties.name(),
                currentKnowledgeVersion(),
                properties.environment(),
                false,
                "POC_NO_LOGIN",
                capabilities
        );
    }

    public XiaomingProductProperties properties() {
        return properties;
    }

    public String currentKnowledgeVersion() {
        return snapshotStore.current()
                .map(snapshot -> snapshot.knowledgeVersion().versionId())
                .orElse(properties.currentKnowledgeVersion());
    }
}
