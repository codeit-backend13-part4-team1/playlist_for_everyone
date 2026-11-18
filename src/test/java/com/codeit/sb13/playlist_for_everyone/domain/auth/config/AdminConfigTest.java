package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;


class AdminConfigTest {

    // 실제 서버 계정과 관계없는 설정 바인딩 테스트용 값이다.
    private static final String EMAIL = "admin@example.com";
    private static final String NAME = "초기 관리자";
    private static final String PASSWORD = "AdminPassword123!";

    private final AdminInitializationService initializationService =
            mock(AdminInitializationService.class);

    // 전체 애플리케이션 대신 관리자 초기화 설정만 불러온다.
    // DB를 사용하는 서비스는 Mock으로 제공한다.
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AdminConfig.class)
                    .withBean(
                            AdminInitializationService.class,
                            () -> initializationService
                    );

    @Test
    @DisplayName("관리자 초기화를 활성화하면 설정을 바인딩하고 초기화 실행 객체를 등록한다")
    void 활성화하면_설정과_초기화_실행_객체를_등록한다() {
        contextRunner
                .withPropertyValues(
                        "everyone.admin.enabled=true",
                        "everyone.admin.email=" + EMAIL,
                        "everyone.admin.name=" + NAME,
                        "everyone.admin.password=" + PASSWORD
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdminProperties.class);
                    assertThat(context).hasSingleBean(AdminInitializer.class);

                    AdminProperties properties =
                            context.getBean(AdminProperties.class);

                    // 외부 설정이 record의 각 필드에 연결됐는지 확인한다.
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.email()).isEqualTo(EMAIL);
                    assertThat(properties.name()).isEqualTo(NAME);
                    assertThat(properties.password()).isEqualTo(PASSWORD);

                    // Bean 등록 자체로 계정을 생성해서는 안 된다.
                    // 실제 실행은 서버 시작 시 ApplicationRunner가 담당한다.
                    verifyNoInteractions(initializationService);
                });
    }

    @Test
    @DisplayName("관리자 초기화를 비활성화하면 초기화 실행 객체를 등록하지 않는다")
    void 비활성화하면_초기화_실행_객체를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("everyone.admin.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdminProperties.class);
                    assertThat(context).doesNotHaveBean(AdminInitializer.class);

                    AdminProperties properties =
                            context.getBean(AdminProperties.class);

                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.email()).isNull();
                    assertThat(properties.name()).isNull();
                    assertThat(properties.password()).isNull();

                    verifyNoInteractions(initializationService);
                });
    }

    @Test
    @DisplayName("관리자 초기화 활성화 설정이 없으면 초기화 실행 객체를 등록하지 않는다")
    void 활성화_설정이_없으면_초기화_실행_객체를_등록하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AdminProperties.class);
            assertThat(context).doesNotHaveBean(AdminInitializer.class);

            AdminProperties properties =
                    context.getBean(AdminProperties.class);

            // 활성화를 명시하지 않으면 기본적으로 초기화를 실행하지 않는다.
            assertThat(properties.enabled()).isFalse();

            verifyNoInteractions(initializationService);
        });
    }

}