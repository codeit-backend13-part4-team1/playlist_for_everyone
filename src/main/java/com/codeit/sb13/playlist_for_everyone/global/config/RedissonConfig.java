package com.codeit.sb13.playlist_for_everyone.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class RedissonConfig {

    // 분산 락에 사용할 RedissonClient를 등록한다.
    // 기존 Redis 연결 설정을 재사용하며, 애플리케이션 종료 시 연결을 정리한다.
    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redisson(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port
    ) {
        Config config = new Config();

        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);

        return Redisson.create(config);
    }

}
