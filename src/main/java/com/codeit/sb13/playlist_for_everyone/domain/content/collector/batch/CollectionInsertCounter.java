package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CollectionInsertCounter {

    private final AtomicLong count = new AtomicLong();
    private final AtomicLong updatedCount = new AtomicLong();
    private final Set<UUID> changedContentIds = ConcurrentHashMap.newKeySet();

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

    /**
     * 커밋된 신규 저장 및 갱신 콘텐츠 ID 목록을 반환한다.
     */
    public List<UUID> getChangedContentIds() {
        return List.copyOf(changedContentIds);
    }
}