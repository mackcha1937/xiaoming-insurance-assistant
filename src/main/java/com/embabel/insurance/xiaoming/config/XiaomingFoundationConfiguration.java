package com.embabel.insurance.xiaoming.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        XiaomingProductProperties.class,
        XiaomingConversationProperties.class,
        XiaomingHealthProperties.class,
        XiaomingApplicationProperties.class,
        XiaomingFeedbackProperties.class
})
public class XiaomingFoundationConfiguration {
}
