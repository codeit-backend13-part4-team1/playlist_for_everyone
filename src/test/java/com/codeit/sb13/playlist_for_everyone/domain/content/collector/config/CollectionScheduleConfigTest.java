package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.batch.core.job.Job;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionScheduleConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(JobOperator.class, () -> mock(JobOperator.class))
                    .withBean("tmdbCollectionJob", Job.class, () -> mock(Job.class))
                    .withBean("sportsDbCollectionJob", Job.class, () -> mock(Job.class))
                    .withUserConfiguration(CollectionScheduleConfig.class);

    @Test
    void 설정이_없으면_정기_수집_설정을_등록하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CollectionScheduleConfig.class);
            assertThat(context).doesNotHaveBean("contentCollectionTaskScheduler");
            assertThat(context).doesNotHaveBean(CollectionJobLauncher.class);
        });
    }

    @Test
    void 비활성화하면_정기_수집_설정을_등록하지_않는다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CollectionScheduleConfig.class);
                    assertThat(context).doesNotHaveBean("contentCollectionTaskScheduler");
                    assertThat(context).doesNotHaveBean(CollectionJobLauncher.class);
                });
    }

    @Test
    void 활성화하면_정기_수집_설정을_등록한다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CollectionScheduleConfig.class);
                    assertThat(context).hasBean("contentCollectionTaskScheduler");
                    assertThat(context).hasSingleBean(CollectionJobLauncher.class);
                });
    }
}