package com.codeit.sb13.playlist_for_everyone.domain.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationDeduplicationProperties.class)
public class NotificationDeduplicationConfig {
}
