package com.codeit.sb13.playlist_for_everyone.global.websocket.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 프로필 YAML의 Origin 바인딩만 검증하며 DB·Redis나 .env.dev는 로딩하지 않는다. */
@DisplayName("WebSocket Origin 필수 설정과 프로필 검증")
class WebSocketOriginPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withInitializer(context -> {
                // 개발자의 환경변수나 JVM 옵션 때문에 필수값 누락 테스트가 통과하지 않도록 격리한다.
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            });

    @Test
    @DisplayName("허용 Origin 설정이 누락되면 기동에 실패한다.")
    void failsToStartWhenOriginsAreMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "Origin=[{0}]")
    @ValueSource(strings = {
            "", "  ", ",", "https://frontend.example,",
            "*", "https://*.example.com", "ws://localhost:8080",
            "frontend.example", "https://frontend.example/",
            "https://frontend.example/ws", "https://frontend.example?key=value",
            "https://frontend.example#/conversations", "https://user@frontend.example",
            "http://localhost:0", "http://localhost:65536", "${MISSING_ORIGIN}"
    })
    @DisplayName("빈 목록·공백·잘못된 Origin은 기동 시 거절한다.")
    void failsToStartWhenOriginsAreInvalid(String origins) {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("invalid-origins", Map.of(
                                "everyone.websocket.allowed-origins", origins))))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "설정={0}, 저장={1}")
    @CsvSource({
            "http://frontend.example:80, http://frontend.example",
            "https://frontend.example:443, https://frontend.example",
            "http://frontend.example:8080, http://frontend.example:8080",
            "https://frontend.example:8443, https://frontend.example:8443",
            "http://[::1]:80, http://[::1]",
            "https://[::1]:443, https://[::1]"
    })
    @DisplayName("Origin의 기본 포트만 제거하고 비기본 포트와 IPv6 주소 형식은 유지한다.")
    void normalizesDefaultPortsAndPreservesOtherPorts(String origin, String expectedOrigin) {
        contextRunner.withPropertyValues("everyone.websocket.allowed-origins=" + origin)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                            .containsExactly(expectedOrigin);
                });
    }

    @Test
    @DisplayName("여러 Origin의 기본 포트를 정규화하고 비기본 포트와 목록 순서는 유지한다.")
    void bindsMultipleOrigins() {
        contextRunner.withPropertyValues(
                        "everyone.websocket.allowed-origins=http://localhost:8080,https://frontend.example:443,"
                                + "http://second.example:80,https://plain.example")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                            .containsExactly("http://localhost:8080", "https://frontend.example",
                                    "http://second.example", "https://plain.example");
                });
    }

    @Test
    @DisplayName("dev에서 환경변수가 없으면 localhost 8080을 기본 Origin으로 사용한다.")
    void devDefaultsToLocalhost() {
        withProfile("dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                    .containsExactly("http://localhost:8080");
        });
    }

    @Test
    @DisplayName("dev의 기본 Origin은 변경한 로컬 서버 포트를 사용한다.")
    void devDefaultUsesConfiguredServerPort() {
        withProfile("dev").withPropertyValues("EVERYONE_SERVER_PORT=9090")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                            .containsExactly("http://localhost:9090");
                });
    }

    @ParameterizedTest(name = "프로필={0}")
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("dev와 prod는 외부에서 주입한 허용 Origin을 사용한다.")
    void profileUsesConfiguredOrigins(String profile) {
        withProfile(profile).withPropertyValues(
                        "EVERYONE_WEBSOCKET_ALLOWED_ORIGINS=https://frontend.example,https://second.example")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                            .containsExactly("https://frontend.example", "https://second.example");
                });
    }

    @Test
    @DisplayName("prod에서 Origin 환경변수가 없으면 localhost로 대체하지 않고 기동에 실패한다.")
    void prodFailsWithoutEnvironmentVariable() {
        withProfile("prod").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest(name = "프로필={0}")
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("Origin 환경변수를 빈 값으로 지정하면 프로필에 관계없이 기동에 실패한다.")
    void profileFailsWithEmptyEnvironmentVariable(String profile) {
        withProfile(profile).withPropertyValues("EVERYONE_WEBSOCKET_ALLOWED_ORIGINS=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("test는 운영 Origin 환경변수 없이 테스트용 localhost 설정으로 기동한다.")
    void testProfileHasIndependentOrigins() {
        withProfile("test").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebSocketOriginProperties.class).allowedOrigins())
                    .containsExactly("http://localhost:8080");
        });
    }

    private ApplicationContextRunner withProfile(String profile) {
        return contextRunner.withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("profile-" + profile,
                                new ClassPathResource("application-" + profile + ".yaml"))
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WebSocketOriginProperties.class)
    static class PropertiesConfig {
    }
}
