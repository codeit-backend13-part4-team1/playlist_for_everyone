package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.listener;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.CollectionInsertCounter;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.ContentCollectionFinishedEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

class CollectionCacheEvictJobListenerTest {

    @ParameterizedTest
    @EnumSource(value = BatchStatus.class, names = {"COMPLETED", "FAILED"})
    void 커밋된_신규_콘텐츠가_있으면_종료_이벤트를_한번_발행한다(
            BatchStatus status
    ) {
        var counter = mock(CollectionInsertCounter.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var listener = new CollectionCacheEvictJobListener(counter, publisher);

        when(counter.getCount()).thenReturn(3L);

        listener.afterJob(execution(status));

        verify(publisher).publishEvent(new ContentCollectionFinishedEvent(3L, 0L));
        verifyNoMoreInteractions(publisher);
    }

    @ParameterizedTest
    @EnumSource(value = BatchStatus.class, names = {"COMPLETED", "FAILED"})
    void 신규_콘텐츠가_없으면_종료_이벤트를_발행하지_않는다(
            BatchStatus status
    ) {
        var counter = mock(CollectionInsertCounter.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var listener = new CollectionCacheEvictJobListener(counter, publisher);

        when(counter.getCount()).thenReturn(0L);

        listener.afterJob(execution(status));

        verifyNoInteractions(publisher);
    }

    private JobExecution execution(BatchStatus status) {
        var execution = new JobExecution(
                1L,
                new JobInstance(1L, "testCollectionJob"),
                new JobParametersBuilder().toJobParameters()
        );
        execution.setStatus(status);
        return execution;
    }
}