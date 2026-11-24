package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CollectionJobLockTest {

    private static final long TMDB_LOCK_KEY = 254_001L;
    private static final long SPORTS_DB_LOCK_KEY = 254_002L;

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16");

    @Test
    void 같은_작업의_잠금은_한쪽만_얻고_반납하면_다시_얻는다() throws Exception {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );

        var first = new CollectionJobLock(dataSource);
        var second = new CollectionJobLock(dataSource);

        try (var firstLock = first.tryAcquire(TMDB_LOCK_KEY).orElseThrow()) {
            assertThat(firstLock).isNotNull();
            assertThat(second.tryAcquire(TMDB_LOCK_KEY)).isEmpty();
        }

        try (var secondLock = second.tryAcquire(TMDB_LOCK_KEY).orElseThrow()) {
            assertThat(secondLock).isNotNull();
        }
    }

    @Test
    void 서로_다른_수집_작업은_각각_잠금을_얻는다() throws Exception {
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );

        var first = new CollectionJobLock(dataSource);
        var second = new CollectionJobLock(dataSource);

        try (
                var tmdbLock = first.tryAcquire(TMDB_LOCK_KEY).orElseThrow();
                var sportsLock = second.tryAcquire(SPORTS_DB_LOCK_KEY).orElseThrow()
        ) {
            assertThat(tmdbLock).isNotNull();
            assertThat(sportsLock).isNotNull();
        }
    }

    @Test
    void 풀의_연결을_반납하면_다른_연결이_잠금을_얻는다() throws Exception {
        try (var pool = new HikariDataSource()) {
            pool.setJdbcUrl(postgres.getJdbcUrl());
            pool.setUsername(postgres.getUsername());
            pool.setPassword(postgres.getPassword());
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(1_000);

            var pooledLock = new CollectionJobLock(pool);
            var otherLock = new CollectionJobLock(
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(),
                            postgres.getUsername(),
                            postgres.getPassword()
                    )
            );

            try (var handle = otherLock.tryAcquire(TMDB_LOCK_KEY).orElseThrow()) {
                assertThat(handle).isNotNull();
                assertThat(pooledLock.tryAcquire(TMDB_LOCK_KEY)).isEmpty();
                assertThat(pooledLock.tryAcquire(TMDB_LOCK_KEY)).isEmpty();
            }

            try (var handle = pooledLock.tryAcquire(TMDB_LOCK_KEY).orElseThrow()) {
                assertThat(handle).isNotNull();
                assertThat(otherLock.tryAcquire(TMDB_LOCK_KEY)).isEmpty();
            }

            try (var handle = otherLock.tryAcquire(TMDB_LOCK_KEY).orElseThrow()) {
                assertThat(handle).isNotNull();
            }

            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        }
    }
}