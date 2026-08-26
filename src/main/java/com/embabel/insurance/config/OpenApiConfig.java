package com.embabel.insurance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI（Swagger）文档配置，生成智能保险平台 API 文档页面。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insurancePlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小明智能投保助手 API")
                        .description("""
                                达尔文12号重大疾病保险（互联网）的单产品限定域内部 POC。

                                `/api/v1/xiaoming` 在 POC 阶段免登录；正式上线使用邀请码准入。
                                旧车险示例接口保留 HTTP Basic Auth，仅作为回归资产。
                                """)
                        .version("2.0.0-phase01")
                        .contact(new Contact()
                                .name("Insurance Platform Team")
                                .email("support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication")));
    }
}
