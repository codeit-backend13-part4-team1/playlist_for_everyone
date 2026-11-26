package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionInsertCounterTest {

    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                    new DataSourceTransactionManager(
                            new DriverManagerDataSource(
                                    "jdbc:h2:mem:collection-counter-" + UUID.randomUUID(),
                                    "sa",
                                    ""
                            )
                    )
            );

    @Test
    void 커밋이_완료된_뒤에만_신규_건수가_증가한다() {
        var counter = new CollectionInsertCounter();
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            counter.recordInserted(contentId);

            // 아직 커밋되지 않았으므로 집계하지 않는다.
            assertThat(counter.getCount()).isZero();
        });

        // 트랜잭션이 커밋된 이후
        assertThat(counter.getCount()).isEqualTo(1L);
        assertThat(counter.getChangedContentIds()).containsExactly(contentId);
    }

    @Test
    void 롤백하면_신규_건수가_증가하지_않는다() {
        var counter = new CollectionInsertCounter();
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            counter.recordInserted(contentId);
            status.setRollbackOnly();
        });

        assertThat(counter.getCount()).isZero();
    }

    @Test
    void 커밋이_완료된_뒤에만_갱신_건수가_증가한다() {
        var counter = new CollectionInsertCounter();
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            counter.recordUpdated(contentId);

            assertThat(counter.getUpdatedCount()).isZero();
        });

        assertThat(counter.getUpdatedCount()).isEqualTo(1L);
        assertThat(counter.getCount()).isZero();
        assertThat(counter.getChangedContentIds()).containsExactly(contentId);
    }

    @Test
    void 롤백하면_갱신_건수가_증가하지_않는다() {
        var counter = new CollectionInsertCounter();
        UUID contentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            counter.recordUpdated(contentId);
            status.setRollbackOnly();
        });

        assertThat(counter.getUpdatedCount()).isZero();
    }
}