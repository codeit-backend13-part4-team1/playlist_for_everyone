package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "everyone.collection.schedule",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class CollectionScheduleConfig {
}