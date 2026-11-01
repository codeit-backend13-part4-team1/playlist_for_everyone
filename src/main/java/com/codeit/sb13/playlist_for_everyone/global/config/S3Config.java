package com.codeit.sb13.playlist_for_everyone.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()));

        if (StringUtils.hasText(props.endpoint())) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")
                    ));
        } else {
            builder.forcePathStyle(false)
                    .credentialsProvider(DefaultCredentialsProvider.builder().build());
        }

        return builder.build();
    }

}
