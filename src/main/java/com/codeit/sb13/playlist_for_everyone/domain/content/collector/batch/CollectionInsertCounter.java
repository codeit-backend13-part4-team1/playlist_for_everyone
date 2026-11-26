package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicLong;

public class CollectionInsertCounter {

    private final AtomicLong count = new AtomicLong();
    private final AtomicLong updatedCount = new AtomicLong();

    public void recordInserted() {
        recordAfterCommit(count);
    }

    public void recordUpdated() {
        recordAfterCommit(updatedCount);
    }

    private void recordAfterCommit(AtomicLong target) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "수집 결과 집계는 트랜잭션 안에서 호출해야 합니다."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        target.incrementAndGet();
                    }
                }
        );
    }

    public long getCount() {
        return count.get();
    }

    public long getUpdatedCount() {
        return updatedCount.get();
    }
}