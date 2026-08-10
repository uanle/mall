package com.resume.mall.seckill.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI seckillOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("BearerAuth", bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Gateway server")));
    }

    @Bean
    public OperationCustomizer accessTokenHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean exists = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(parameter -> "accessToken".equals(parameter.getName()));
            if (!exists) {
                operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("accessToken")
                        .description("Swagger 测试用：填写登录响应里的 accessToken，不需要 Bearer 前缀。")
                        .required(false)
                        .schema(new StringSchema().example("eyJhbGciOiJIUzI1NiJ9...")));
            }
            return operation;
        };
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
