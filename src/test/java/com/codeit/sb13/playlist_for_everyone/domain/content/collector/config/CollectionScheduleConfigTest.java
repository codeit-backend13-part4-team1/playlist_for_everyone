package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
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
                    .withBean(JobRepository.class, () -> mock(JobRepository.class))
                    .withUserConfiguration(CollectionScheduleConfig.class,
                            SchedulingTestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingTestConfig {
    }

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

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void cron을_설정한_수집_작업만_예약한다(String provider) {
        String cron = "0 0 3 * * *";

        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.enabled=true",
                        "everyone.collection.schedule." + provider + ".cron=" + cron,
                        "everyone.collection.schedule.sportsdb.league-id=4328",
                        "everyone.collection.schedule.sportsdb.season=2014-2015"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var processor = context.getBean(
                            ScheduledAnnotationBeanPostProcessor.class
                    );
                    var tasks = processor.getScheduledTasks();

                    assertThat(tasks).hasSize(1);

                    var task = tasks.iterator().next().getTask();
                    assertThat(task).isInstanceOf(CronTask.class);
                    assertThat(((CronTask) task).getExpression())
                            .isEqualTo(cron);
                });
    }

    @Test
    void cron이_비활성화_값이면_수집_작업을_예약하지_않는다() {
        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.enabled=true",
                        "everyone.collection.schedule.tmdb.cron=-",
                        "everyone.collection.schedule.sportsdb.cron=-"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var processor = context.getBean(
                            ScheduledAnnotationBeanPostProcessor.class
                    );

                    assertThat(processor.getScheduledTasks()).isEmpty();
                });
    }
}