package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 서버 시작 시 관리자 초기화 서비스를 호출한다.
 * 계정 생성 여부에 대한 판단은 초기화 서비스에 위임한다
 */
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final AdminInitializationService adminInitializationService;


    @Override
    public void run(ApplicationArguments args) {
        // RED : 테스트로 실행 조건을 정의한 뒤 구현한다.
    }
}

