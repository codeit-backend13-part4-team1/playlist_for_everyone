package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig{

    @Bean
    @ConditionalOnProperty(
            prefix = "everyone.admin",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public AdminInitializer adminInitializer(AdminProperties adminProperties,
                                             AdminInitializationService adminInitializationService) {

        // 명시적으로 활성화한 환경에서만 서버 시작 시 초기화를 실행한다.
        return new AdminInitializer(adminProperties, adminInitializationService);
    }
}
