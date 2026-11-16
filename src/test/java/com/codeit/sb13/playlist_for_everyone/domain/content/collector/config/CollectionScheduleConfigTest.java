package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionScheduleConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CollectionScheduleConfig.class);

    @Test
    void 설정이_없으면_정기_수집_설정을_등록하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .doesNotHaveBean(CollectionScheduleConfig.class);
        });
    }

    @Test
    void 비활성화하면_정기_수집_설정을_등록하지_않는다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .doesNotHaveBean(CollectionScheduleConfig.class);
                });
    }

    @Test
    void 활성화하면_정기_수집_설정을_등록한다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(CollectionScheduleConfig.class);
                });
    }
}