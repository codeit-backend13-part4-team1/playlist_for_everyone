package com.codeit.sb13.playlist_for_everyone.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI의 서버 정보와 재사용할 인증 방식을 정의한다.
 *
 * <p>bearerAuth 등록만으로 모든 API에 인증 요구사항이 붙지는 않는다.
 * 각 API의 SecurityRequirement가 같은 이름을 참조하며, 실제 요청 차단은 SecurityConfig가 담당한다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(
            @Value("${everyone.openapi.server-url:http://localhost:8080}") String serverUrl,
            @Value("${everyone.openapi.server-description:로컬 개발 서버}") String serverDescription
    ) {
        Server server = new Server()
                .url(serverUrl)
                .description(serverDescription);

        Info info = new Info()
                .title("모두의 플리 API 문서")
                .description("모두의 플리 Swagger API 문서입니다.")
                .version("v1");

        // Swagger에서 입력한 JWT를 Authorization: Bearer 헤더로 전달하기 위한 문서 설정이다.
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(info)
                .servers(List.of(server))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerAuth));
    }
}
