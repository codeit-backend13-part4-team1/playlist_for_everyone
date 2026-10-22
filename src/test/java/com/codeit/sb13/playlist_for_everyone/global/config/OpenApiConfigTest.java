package com.codeit.sb13.playlist_for_everyone.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("OpenAPI 문서 설정")
class OpenApiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class);

    @Test
    @DisplayName("서버 설정이 없으면 기본 API 문서 정보와 로컬 서버를 등록한다")
    void usesLocalServerDefaultsWhenPropertiesAreAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            OpenAPI api = context.getBean(OpenAPI.class);

            assertThat(api.getInfo().getTitle()).isEqualTo("모두의 플리 API 문서");
            assertThat(api.getInfo().getDescription()).isEqualTo("모두의 플리 Swagger API 문서입니다.");
            assertThat(api.getInfo().getVersion()).isEqualTo("v1");
            assertThat(api.getServers()).singleElement().satisfies(server -> {
                assertThat(server.getUrl()).isEqualTo("http://localhost:8080");
                assertThat(server.getDescription()).isEqualTo("로컬 개발 서버");
            });
        });
    }

    @Test
    @DisplayName("서버 설정이 있으면 지정한 URL과 설명을 OpenAPI에 반영한다")
    void usesConfiguredServerUrlAndDescription() {
        contextRunner.withPropertyValues(
                "everyone.openapi.server-url=https://api.example.test",
                "everyone.openapi.server-description=테스트 서버"
        ).run(context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            assertThat(context.getBean(OpenAPI.class).getServers()).singleElement().satisfies(server -> {
                assertThat(server.getUrl()).isEqualTo("https://api.example.test");
                assertThat(server.getDescription()).isEqualTo("테스트 서버");
            });
        });
    }
}
