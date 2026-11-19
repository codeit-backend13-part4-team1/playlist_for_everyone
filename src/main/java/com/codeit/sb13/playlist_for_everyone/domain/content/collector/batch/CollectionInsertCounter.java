package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicLong;

public class CollectionInsertCounter {

    private final AtomicLong count = new AtomicLong();

    public void recordInserted() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "신규 저장 집계는 트랜잭션 안에서 호출해야 합니다."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        count.incrementAndGet();
                    }
                }
        );
    }

    public long getCount() {
        return count.get();
    }
}