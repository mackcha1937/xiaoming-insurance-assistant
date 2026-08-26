package com.embabel.insurance.xiaoming.context;

import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import com.embabel.insurance.xiaoming.contract.XiaomingIntent;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ProductContextService {

    private final XiaomingProductProperties properties;

    public ProductContextService(XiaomingProductProperties properties) {
        this.properties = properties;
    }

    public ProductContext current() {
        List<CapabilityDescriptor> capabilities = Arrays.stream(XiaomingIntent.values())
                .map(intent -> new CapabilityDescriptor(intent, intent.getLabel(), false))
                .toList();

        return new ProductContext(
                properties.productId(),
                properties.name(),
                properties.currentKnowledgeVersion(),
                properties.environment(),
                false,
                "POC_NO_LOGIN",
                capabilities
        );
    }

    public XiaomingProductProperties properties() {
        return properties;
    }
}
