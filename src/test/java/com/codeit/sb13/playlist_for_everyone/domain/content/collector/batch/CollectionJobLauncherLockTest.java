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
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener.CollectionExecutionOwnerListener;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DriverManager;
import java.util.Set;

import static org.mockito.Mockito.verify;
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
                new CollectionJobLock(dataSource),
                mock(CollectionExecutionRecoveryPolicy.class)
        );

        var secondLauncher = new CollectionJobLauncher(
                secondOperator,
                tmdbJob,
                sportsDbJob,
                secondRepository,
                new CollectionJobLock(dataSource),
                mock(CollectionExecutionRecoveryPolicy.class)
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

    @ParameterizedTest
    @ValueSource(strings = {"tmdb", "sportsdb"})
    void 잠금_연결이_끊겨도_기존_태스크가_살아_있으면_복구하지_않는다(
            String provider
    ) throws Exception {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        var jdbc = new JdbcTemplate(dataSource);
        var jobLock = new CollectionJobLock(dataSource);

        var tmdbJob = mock(Job.class);
        var sportsDbJob = mock(Job.class);

        when(tmdbJob.getName()).thenReturn("tmdbCollectionJob");
        when(sportsDbJob.getName()).thenReturn("sportsDbCollectionJob");

        Job job = "tmdb".equals(provider) ? tmdbJob : sportsDbJob;
        long lockKey = "tmdb".equals(provider) ? 254_001L : 254_002L;

        var owner = new CollectionExecutionOwner(
                "arn:aws:ecs:ap-northeast-2:123456789012:cluster/test",
                "arn:aws:ecs:ap-northeast-2:123456789012:task/test/previous"
        );
        var context = new ExecutionContext();

        context.putString(CollectionExecutionOwnerListener.CLUSTER_ARN_KEY, owner.clusterArn());
        context.putString(CollectionExecutionOwnerListener.TASK_ARN_KEY, owner.taskArn());

        var execution = mock(JobExecution.class);
        var repository = mock(JobRepository.class);
        var operator = mock(JobOperator.class);
        var statusClient = mock(EcsTaskStatusClient.class);

        when(execution.getExecutionContext()).thenReturn(context);
        when(repository.findRunningJobExecutions(job.getName()))
                .thenReturn(Set.of(execution));
        when(statusClient.isStopped(owner)).thenReturn(false);

        var launcher = new CollectionJobLauncher(
                operator,
                tmdbJob,
                sportsDbJob,
                repository,
                jobLock,
                new CollectionExecutionRecoveryPolicy(statusClient)
        );

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        )) {
            int ownerPid;

            try (
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT pg_backend_pid()")
            ) {
                assertThat(result.next()).isTrue();
                ownerPid = result.getInt(1);
            }

            // 기존 서버의 DB 연결이 잠금을 보유
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, lockKey);

                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
            }

            assertThat(jobLock.tryAcquire(lockKey)).isEmpty();

            Boolean terminated = jdbc.queryForObject(
                    "SELECT pg_terminate_backend(?, 5000)",
                    Boolean.class,
                    ownerPid
            );
            assertThat(terminated).isTrue();

            // DB 연결은 끊겼지만 기존 ECS 태스크는 실행 중
            start(launcher, provider);

            verify(statusClient).isStopped(owner);
            verifyNoInteractions(operator);

            assertThat(context.containsKey(CollectionExecutionRecoveryPolicy.RESTART_PENDING_KEY))
                    .isFalse();
        }

        // 보류 후 잠금이 반납되어야 다음 예약도 확인 가능
        try (var handle = jobLock.tryAcquire(lockKey).orElseThrow()) {
            assertThat(handle).isNotNull();
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