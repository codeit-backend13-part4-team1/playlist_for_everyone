package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DirectMessageTransportProperties.class)
public class DirectMessageTransportConfig {
}
