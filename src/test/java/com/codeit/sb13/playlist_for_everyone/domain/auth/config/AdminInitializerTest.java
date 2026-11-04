package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    private static final String EMAIL = "admin@example.com";
    private static final String NAME = "초기 관리자";
    private static final String PASSWORD = "AdminPassword123!";

    @Mock
    private AdminInitializationService  adminInitializationService;

    @Test
    @DisplayName("관리자 초기화가 활성화되면 설정값으로 초기화 서비스를 호출한다")
    void 초기화가_활성화되면_서비스를_호출한다() {
        AdminProperties properties = new AdminProperties(
                true,
                EMAIL,
                NAME,
                PASSWORD
        );

        AdminInitializer initializer = new AdminInitializer(
                properties,
                adminInitializationService
        );

        // 서버 시작 시 호출되는 메서드를 직접 실행한다.
        initializer.run(new DefaultApplicationArguments(new String[0]));

        // 설정된 값을 서비스에 그대로 전달하는지 검증한다.
        verify(adminInitializationService).initialize(
                EMAIL,
                NAME,
                PASSWORD
        );

        verifyNoMoreInteractions(adminInitializationService);
    }

    @Test
    @DisplayName("관리자 초기화가 비활성화되면 계정 설정 없이도 서비스를 호출하지 않는다.")
    void 초기화가_비활성화되면_서비스를_호출하지_않는다() {
        // 초기화를 사용하지 않는 환경에서는 계정 정보를 요구하지 않는다.
        AdminProperties properties = new AdminProperties(
                false,
                null,
                null,
                null
        );

        AdminInitializer initializer = new AdminInitializer(
                properties,
                adminInitializationService
        );

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(adminInitializationService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("관리자 초기화가 활성화되어 있고 이메일이 없으면 예외를 발생시킨다.")
    void 초기화_이메일이_없으면_거절한다(String email) {
        AdminProperties properties =
                new AdminProperties(true, email, NAME, PASSWORD);

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        // 설정 누락을 관리자 생성 전에 발견하고, 문제가 된 설정 이름을 알려준다.
        assertThatThrownBy(() ->
                initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("everyone.admin.email");

        // 잘못된 설정으로 계정을 조회하거나 생성하지 않는다.
        verifyNoInteractions(adminInitializationService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("관리자 초기화가 활성화되어 있고 이름이 없으면 예외를 발생시킨다")
    void 초기화_이름이_없으면_거절한다(String name) {
        AdminProperties properties =
                new AdminProperties(true, EMAIL, name, PASSWORD);

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        assertThatThrownBy(() ->
                initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("everyone.admin.name");

        verifyNoInteractions(adminInitializationService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("관리자 초기화가 활성화되어 있고 비밀번호가 없으면 예외를 발생시킨다")
    void 초기화_비밀번호가_없으면_거절한다(String password) {
        AdminProperties properties =
                new AdminProperties(true, EMAIL, NAME, password);

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        // 오류 메시지에는 비밀번호 값이 아닌 설정 이름만 안내한다.
        assertThatThrownBy(() ->
                initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("everyone.admin.password");

        verifyNoInteractions(adminInitializationService);
    }

    @ParameterizedTest
    @MethodSource("passwordsWithinByteLimit")
    @DisplayName("관리자 비밀번호가 UTF-8 기준 72바이트이면 초기화를 허용한다")
    void 비밀번호가_72바이트이면_허용한다(String password) {
        AdminProperties properties =
                new AdminProperties(true, EMAIL, NAME, password);

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        // 경계값을 허용하고, 원문을 변경하지 않은 채 서비스에 전달한다.
        verify(adminInitializationService).initialize(EMAIL, NAME, password);
        verifyNoMoreInteractions(adminInitializationService);
    }

    static Stream<String> passwordsWithinByteLimit() {
        return Stream.of(
                "a".repeat(72),   // ASCII 문자는 UTF-8 기준 1바이트다.
                "가".repeat(24)   // '가'는 UTF-8 기준 3바이트다.
        );
    }

    @ParameterizedTest
    @MethodSource("passwordsExceedingByteLimit")
    @DisplayName("관리자 비밀번호가 UTF-8 기준 72바이트를 초과하면 거절한다")
    void 비밀번호가_72바이트를_초과하면_거절한다(String password) {
        AdminProperties properties =
                new AdminProperties(true, EMAIL, NAME, password);

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        assertThatThrownBy(() ->
                initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("everyone.admin.password")
                .hasMessageContaining("72바이트")
                // 설정 오류를 알려주되 비밀번호 원문은 노출하지 않는다.
                .hasMessageNotContaining(password);

        // 잘못된 설정은 계정 조회나 저장을 시작하기 전에 거절한다.
        verifyNoInteractions(adminInitializationService);
    }

    static Stream<String> passwordsExceedingByteLimit() {
        return Stream.of(
                "a".repeat(73),        // ASCII 문자로 73바이트
                "가".repeat(24) + "a"  // 25글자이지만 UTF-8 기준 73바이트
        );
    }

    @Test
    @DisplayName("관리자 초기화가 비활성화되면 비밀번호 바이트 제한을 검사하지 않는다")
    void 비활성화되면_긴_비밀번호도_검사하지_않는다() {
        AdminProperties properties =
                new AdminProperties(false, EMAIL, NAME, "a".repeat(73));

        AdminInitializer initializer =
                new AdminInitializer(properties, adminInitializationService);

        // 사용하지 않는 관리자 설정 때문에 서버 시작이 실패하면 안 된다.
        initializer.run(new DefaultApplicationArguments(new String[0]));

        verifyNoInteractions(adminInitializationService);
    }




}