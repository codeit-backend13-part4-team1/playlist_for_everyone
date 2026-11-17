package com.codeit.sb13.playlist_for_everyone.domain.conversation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** DM 전송 설정을 바인딩하고 생성자 검증을 적용한다. 구현체 선택·구독 여부는 각 빈의 조건이 담당한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DirectMessageTransportProperties.class)
public class DirectMessageTransportConfig {
}
