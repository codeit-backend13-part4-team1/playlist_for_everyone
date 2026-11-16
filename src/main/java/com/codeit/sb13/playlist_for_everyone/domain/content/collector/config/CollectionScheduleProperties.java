package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everyone.collection.schedule")
public class CollectionScheduleProperties {

    private boolean enabled = false;
}