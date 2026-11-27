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
    void execute_success() {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

        Runnable action = mock(Runnable.class);

        // when
        contentIndexLock.execute(action);

        // then
        var inOrder = inOrder(lock, action);
        inOrder.verify(lock).lock();
        inOrder.verify(action).run();
        inOrder.verify(lock).unlock();
    }

    @Test
    @DisplayName("분산 락 내부 작업의 반환값을 전달한다.")
    void execute_withReturnValue() {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

        // when
        String result = contentIndexLock.execute(() -> "success");

        // then
        assertThat(result).isEqualTo("success");

        verify(lock).lock();
        verify(lock).unlock();
    }

    @Test
    @DisplayName("작업 중 예외가 발생해도 분산 락을 해제한다.")
    void execute_exception() {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

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
    @DisplayName("분산 락 획득에 실패하면 작업을 실행하지 않는다.")
    void execute_lockFailure() {
        // given
        when(redissonClient.getLock("everyone:lock:content:index"))
                .thenReturn(lock);

        doThrow(new IllegalStateException("락 획득 실패"))
                .when(lock).lock();

        Runnable action = mock(Runnable.class);

        // when & then
        assertThatThrownBy(() -> contentIndexLock.execute(action))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(action);
        verify(lock, never()).unlock();
    }

}
