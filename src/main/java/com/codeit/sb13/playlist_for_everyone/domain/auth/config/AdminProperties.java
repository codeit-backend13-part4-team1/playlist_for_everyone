package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 초기화에 필요한 외부 설정
 * 실제 계정 정보는 환경변수로 전달한다.
 */

@ConfigurationProperties(prefix = "everyone.admin")
public record AdminProperties(
        boolean enabled,
        String email,
        String name,
        String password
) {
}
