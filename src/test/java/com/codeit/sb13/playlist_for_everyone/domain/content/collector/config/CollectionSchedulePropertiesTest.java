package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionSchedulePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class);

    @Test
    void 자동_수집은_기본적으로_꺼져_있다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            var properties = context.getBean(CollectionScheduleProperties.class);

            assertThat(properties.isEnabled()).isFalse();
        });
    }

    @Test
    void 설정으로_자동_수집을_켤_수_있다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties =
                            context.getBean(CollectionScheduleProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CollectionScheduleProperties.class)
    static class TestConfig {
    }
}