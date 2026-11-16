package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.exception.CollectedContentValidationException;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.skip.LimitCheckingExceptionHierarchySkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStream;

import java.util.Set;

public class SportsDbSkipPolicy implements SkipPolicy, ItemStream {

    private static final String SKIP_COUNT_KEY = "sportsDb.skipCount";

    private final StepExecution stepExecution;
    private final LimitCheckingExceptionHierarchySkipPolicy delegate;

    private long previousSkipCount;

    public SportsDbSkipPolicy(
            StepExecution stepExecution,
            long skipLimit
    ) {
        this.stepExecution = stepExecution;
        this.delegate = new LimitCheckingExceptionHierarchySkipPolicy(
                Set.of(CollectedContentValidationException.class),
                skipLimit
        );
    }

    /** 이전에 저장한 건너뛰기 횟수 복원 */
    @Override
    public void open(ExecutionContext context) {
        previousSkipCount = context.getLong(SKIP_COUNT_KEY, 0L);
    }

    /** 이전 실행과 현재 실행의 합계로 한도 확인 */
    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) {
        if (skipCount < 0) {
            return delegate.shouldSkip(throwable, skipCount);
        }

        return delegate.shouldSkip(
                throwable,
                previousSkipCount + skipCount
        );
    }

    /** 읽기 위치와 함께 누적 횟수 저장 */
    @Override
    public void update(ExecutionContext context) {
        context.putLong(
                SKIP_COUNT_KEY,
                previousSkipCount + stepExecution.getSkipCount()
        );
    }
}