package com.codeit.sb13.playlist_for_everyone.global.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "everyone.s3")
public record S3Properties(
        String bucket,
        String region,
        String endpoint
) {
}
