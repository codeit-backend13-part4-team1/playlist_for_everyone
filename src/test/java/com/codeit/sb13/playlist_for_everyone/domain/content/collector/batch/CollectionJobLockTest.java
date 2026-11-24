package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CollectionJobLockTest {

    private static final long TMDB_LOCK_KEY = 254_001L;

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
}