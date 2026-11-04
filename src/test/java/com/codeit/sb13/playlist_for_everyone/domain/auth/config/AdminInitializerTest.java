package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

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
}