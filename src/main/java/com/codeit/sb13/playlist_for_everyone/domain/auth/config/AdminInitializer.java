package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.nio.charset.StandardCharsets;

/**
 * 서버 시작 시 관리자 초기화 서비스를 호출한다.
 * 계정 생성 여부에 대한 판단은 초기화 서비스에 위임한다
 */
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final AdminProperties adminProperties;
    private final AdminInitializationService adminInitializationService;


    @Override
    public void run(ApplicationArguments args) {
        if (!adminProperties.enabled()) {
            return;
        }

        // DB 조회나 저장을 수행하기 전에 필수 설정을 확인한다.
        requireText(adminProperties.email(), "everyone.admin.email");
        requireText(adminProperties.name(), "everyone.admin.name");
        requireText(adminProperties.password(), "everyone.admin.password");

        if (adminProperties.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("everyone.admin.password는 UTF-8 기준 " + MAX_PASSWORD_BYTES + "바이트 이하여야 합니다.");
        }

        adminInitializationService.initialize(
                adminProperties.email(),
                adminProperties.name(),
                adminProperties.password()
        );
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "관리자 초기화에 필요한 설정이 비어 있습니다: " + propertyName
            );
        }
    }
}

