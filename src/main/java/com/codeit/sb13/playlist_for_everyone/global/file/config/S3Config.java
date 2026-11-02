package com.codeit.sb13.playlist_for_everyone.global.file.config;

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
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()));

        // 현재 프로젝트에서는 커스텀 endpoint를 로컬 Mock S3 테스트에만 사용
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

    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()));

        if (StringUtils.hasText(props.endpoint())) {
            // endpoint에 값이 있다면 테스트 환경
            builder.endpointOverride(URI.create(props.endpoint())) // test url로 endpoint를 설정
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            // endpoint에 값이 없다면 진짜 AWS에 요청을 보내야 하는 상황
            builder.serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(false)
                            .build())
                    .credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

}
