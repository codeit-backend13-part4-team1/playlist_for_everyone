package com.codeit.sb13.playlist_for_everyone.domain.auth.config;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.AdminInitializationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfig{

    @Bean
    public AdminInitializer adminInitializer(AdminProperties adminProperties,
                                             AdminInitializationService adminInitializationService) {

        return new AdminInitializer(adminProperties, adminInitializationService);
    }
}
