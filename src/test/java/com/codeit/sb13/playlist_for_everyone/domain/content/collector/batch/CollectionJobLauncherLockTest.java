package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Testcontainers
class CollectionJobLauncherLockTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 다른_서버가_수집_중이면_실행하거나_복구하지_않는다(
            String provider
    ) throws Exception {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );

        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);

        when(tmdbJob.getName()).thenReturn("tmdbCollectionJob");
        when(sportsDbJob.getName()).thenReturn("sportsDbCollectionJob");

        var firstOperator = mock(JobOperator.class);
        var firstRepository = mock(JobRepository.class);
        var secondOperator = mock(JobOperator.class);
        var secondRepository = mock(JobRepository.class);

        var firstLauncher = new CollectionJobLauncher(
                firstOperator,
                tmdbJob,
                sportsDbJob,
                firstRepository,
                new CollectionJobLock(dataSource)
        );

        var secondLauncher = new CollectionJobLauncher(
                secondOperator,
                tmdbJob,
                sportsDbJob,
                secondRepository,
                new CollectionJobLock(dataSource)
        );

        var started = new CountDownLatch(1);
        var finishAllowed = new CountDownLatch(1);

        when(firstOperator.start(any(Job.class), any(JobParameters.class)))
                .thenAnswer(invocation -> {
                    started.countDown();

                    if (!finishAllowed.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("테스트 수집 종료 대기 초과");
                    }

                    return mock(JobExecution.class);
                });

        var executor = Executors.newSingleThreadExecutor();

        try {
            var firstRun = executor.submit(() -> {
                start(firstLauncher, provider);
                return null;
            });

            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

                start(secondLauncher, provider);

                verifyNoInteractions(secondRepository, secondOperator);
            } finally {
                finishAllowed.countDown();
            }

            firstRun.get(10, TimeUnit.SECONDS);
        } finally {
            finishAllowed.countDown();
            executor.shutdownNow();
        }
    }

    private void start(
            CollectionJobLauncher launcher,
            String provider
    ) throws Exception {
        if ("tmdb".equals(provider)) {
            launcher.startTmdb(5L);
        } else {
            launcher.startSportsDb("4328", "2014-2015");
        }
    }
}