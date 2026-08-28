package com.embabel.insurance.xiaoming.application;

import com.embabel.insurance.xiaoming.config.XiaomingApplicationProperties;
import com.embabel.insurance.xiaoming.config.XiaomingProductProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationLinkServiceTest {

    @Test
    void generatesOnlyExactAllowedHttpsDomainAndParameters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApplicationLinkService service = service("uat", "https://apply.example.test/start",
                List.of("apply.example.test"), registry);

        ApplicationLinkResult result = service.create("36743", Map.of(
                "productId", new String[]{"36743"},
                "channelType", new String[]{"advisor"},
                "unknown", new String[]{"discard-me"},
                "healthFacts", new String[]{"never-copy"}
        ));

        assertThat(result.url()).isEqualTo(
                "https://apply.example.test/start?productId=36743&channelType=advisor");
        assertThat(result.url()).doesNotContain("unknown", "healthFacts", "discard-me", "never-copy");
        assertThat(registry.counter("xiaoming.application.parameters.dropped").count()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicatesUnsafeValuesAndUnconfiguredLinks() {
        ApplicationLinkService service = service("uat", "https://apply.example.test/start",
                List.of("apply.example.test"), new SimpleMeterRegistry());
        assertThatThrownBy(() -> service.create("36743", Map.of(
                "productId", new String[]{"36743"},
                "source", new String[]{"one", "two"}
        ))).isInstanceOf(ApplicationLinkException.class)
                .hasMessageContaining("渠道参数");

        assertThatThrownBy(() -> service("uat", "http://apply.example.test/start",
                List.of("apply.example.test"), new SimpleMeterRegistry())
                .create("36743", Map.of("productId", new String[]{"36743"})))
                .isInstanceOf(ApplicationLinkException.class)
                .hasMessageContaining("尚未配置");

        assertThatThrownBy(() -> service.create("36743", Map.of(
                "productId", new String[]{"36743", "other"}
        ))).isInstanceOf(ApplicationLinkException.class)
                .hasMessageContaining("产品参数");
    }

    @Test
    void productionCannotUseUatTestOrSubdomainLookalikes() {
        assertThatThrownBy(() -> service("production", "https://uat.apply.example.com/start",
                List.of("uat.apply.example.com"), new SimpleMeterRegistry())
                .create("36743", Map.of("productId", new String[]{"36743"})))
                .isInstanceOf(ApplicationLinkException.class);

        assertThatThrownBy(() -> service("production", "https://apply.example.com.evil.test/start",
                List.of("apply.example.com"), new SimpleMeterRegistry())
                .create("36743", Map.of("productId", new String[]{"36743"})))
                .isInstanceOf(ApplicationLinkException.class);
    }

    private ApplicationLinkService service(String environment, String url, List<String> domains,
                                           SimpleMeterRegistry registry) {
        return new ApplicationLinkService(
                new XiaomingApplicationProperties(true, url, domains, List.of("channelType", "source"), 300),
                new XiaomingProductProperties("36743", "test product", "knowledge-v1", environment),
                registry
        );
    }
}
