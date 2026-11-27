package com.codeit.sb13.playlist_for_everyone.domain.content.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 색인 분산 락 단위 테스트")
class ContentIndexLockTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private ContentIndexLock contentIndexLock;

    @Test
    @DisplayName("분산 락 획득 후 작업을 실행하고 락을 해제한다.")
    void execute_success() throws InterruptedException {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);
        when(lock.tryLock(30L, TimeUnit.SECONDS))
                .thenReturn(true);

        Runnable action = mock(Runnable.class);

        // when
        contentIndexLock.execute(action);

        // then
        var inOrder = inOrder(lock, action);
        inOrder.verify(lock).tryLock(30L, TimeUnit.SECONDS);
        inOrder.verify(action).run();
        inOrder.verify(lock).unlock();
    }

    @Test
    @DisplayName("분산 락 내부 작업의 반환값을 전달한다.")
    void execute_withReturnValue() throws InterruptedException {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);
        when(lock.tryLock(30L, TimeUnit.SECONDS))
                .thenReturn(true);


        // when
        String result = contentIndexLock.execute(() -> "success");

        // then
        assertThat(result).isEqualTo("success");

        verify(lock).tryLock(30L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("작업 중 예외가 발생해도 분산 락을 해제한다.")
    void execute_exception() throws InterruptedException {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);
        when(lock.tryLock(30L, TimeUnit.SECONDS))
                .thenReturn(true);

        Runnable action = mock(Runnable.class);

        doThrow(new IllegalStateException("색인 실패"))
                .when(action).run();

        // when & then
        assertThatThrownBy(() -> contentIndexLock.execute(action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("색인 실패");

        verify(lock).unlock();
    }

    @Test
    @DisplayName("분산 락 획득 시간 초과 시 작업을 실행하지 않는다.")
    void execute_lockFailure() throws InterruptedException {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

        when(lock.tryLock(30L, TimeUnit.SECONDS))
                .thenReturn(false);

        Runnable action = mock(Runnable.class);

        // when & then
        assertThatThrownBy(() -> contentIndexLock.execute(action))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("콘텐츠 색인 락 획득 시간 초과");

        verifyNoInteractions(action);
        verify(lock, never()).unlock();
    }

}
