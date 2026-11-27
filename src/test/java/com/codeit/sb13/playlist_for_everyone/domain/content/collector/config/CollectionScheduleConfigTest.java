package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionExecutionRecoveryPolicy;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionJobLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.CronTrigger;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CollectionScheduleConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(JobOperator.class, () -> mock(JobOperator.class))
                    .withBean("tmdbCollectionJob", Job.class, () -> mock(Job.class))
                    .withBean("sportsDbCollectionJob", Job.class, () -> mock(Job.class))
                    .withBean(JobRepository.class, () -> mock(JobRepository.class))
                    .withBean(DataSource.class, () -> mock(DataSource.class))
                    .withUserConfiguration(CollectionScheduleConfig.class,
                            SchedulingTestConfig.class)
                    .withBean(CollectionExecutionRecoveryPolicy.class, () -> mock(CollectionExecutionRecoveryPolicy.class));

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
                    assertThat(task).isInstanceOf(TriggerTask.class);

                    var trigger = ((TriggerTask) task).getTrigger();
                    assertThat(trigger).isInstanceOf(CronTrigger.class);
                    assertThat(((CronTrigger) trigger).getExpression())
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

    @Test
    void YAML의_환경변수_설정으로_두_수집_작업을_예약한다() {
        contextRunner
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yaml",
                        "spring.profiles.active=test",
                        "EVERYONE_COLLECTION_SCHEDULE_ENABLED=true",
                        "EVERYONE_COLLECTION_SCHEDULE_ZONE=UTC",
                        "EVERYONE_COLLECTION_SCHEDULE_TMDB_CRON=0 0 3 * * *",
                        "EVERYONE_COLLECTION_SCHEDULE_TMDB_MAX_PAGES=7",
                        "EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_CRON=0 0 4 * * *",
                        "EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_LEAGUE_ID=4328",
                        "EVERYONE_COLLECTION_SCHEDULE_SPORTSDB_SEASON=2014-2015"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties =
                            context.getBean(CollectionScheduleProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getZone()).isEqualTo("UTC");
                    assertThat(properties.getTmdb().getMaxPages()).isEqualTo(7L);
                    assertThat(properties.getSportsdb().getLeagueId())
                            .isEqualTo("4328");
                    assertThat(properties.getSportsdb().getSeason())
                            .isEqualTo("2014-2015");

                    var processor = context.getBean(
                            ScheduledAnnotationBeanPostProcessor.class
                    );

                    assertThat(processor.getScheduledTasks())
                            .extracting(task -> {
                                var triggerTask = (TriggerTask) task.getTask();
                                return ((CronTrigger) triggerTask.getTrigger()).getExpression();
                            })
                            .containsExactlyInAnyOrder(
                                    "0 0 3 * * *",
                                    "0 0 4 * * *"
                            );
                });
    }

    @Test
    void 앱_시작_시에는_잠금을_얻거나_실행을_복구하지_않는다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CollectionJobLauncher.class);

                    verifyNoInteractions(
                            context.getBean(DataSource.class),
                            context.getBean(JobRepository.class),
                            context.getBean(JobOperator.class)
                    );
                });
    }

}