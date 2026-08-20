package com.hwalro.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme =
                new SecurityScheme().type(Type.HTTP).scheme("bearer").bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("HWALRO Auth Service API")
                        .description("JWT 액세스 토큰(Authorization: Bearer 헤더) 기반 인증 API. 리프레시 토큰은 HttpOnly 쿠키로 관리된다.")
                        .version("v1.0.0"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
