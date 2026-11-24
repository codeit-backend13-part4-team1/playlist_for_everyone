package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionRecoveryPolicy;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.EcsTaskStatusClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "everyone.collection.schedule",
        name = "enabled",
        havingValue = "true"
)
public class EcsTaskStatusConfig {

    @Bean(destroyMethod = "close")
    public EcsClient collectionEcsClient(
            @Value("${EVERYONE_AWS_REGION:ap-northeast-2}") String region
    ) {
        return EcsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3))
                )
                .build();
    }

    @Bean
    public EcsTaskStatusClient ecsTaskStatusClient(EcsClient collectionEcsClient) {
        return new EcsTaskStatusClient(collectionEcsClient);
    }

    @Bean
    public CollectionExecutionRecoveryPolicy collectionExecutionRecoveryPolicy(
            EcsTaskStatusClient statusClient
    ) {
        return new CollectionExecutionRecoveryPolicy(statusClient);
    }
}