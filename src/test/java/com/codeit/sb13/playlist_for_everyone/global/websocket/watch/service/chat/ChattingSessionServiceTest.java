package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service.chat;


import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ChatSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ChattingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.chatting.ChattingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("동시 시청 도메인의 채팅 세션 서비스 테스트")
public class ChattingSessionServiceTest {

    @Mock
    ChattingSessionRepository chattingSessionRepository;
    @Mock
    ChatSessionMessageListener chattingSessionMessageListener;
    @Mock
    RedisTopicRegistry redisTopicRegistry;
    // TODO - message 전송 레이어로 변경 예정
    @Mock
    RedisTemplate<String, Object> redisTemplate;
    @Spy
    Locks locks;

    @InjectMocks
    ChattingSessionServiceImpl chattingSessionService;

    // stub 용 필드
    UUID CONTENT_ID = UUID.randomUUID();
    ChattingSessionRoom room = mock(ChattingSessionRoom.class);

    // const
    int TIME_OUT = 5;

    WSSubscription subscription(String sessionId, String subscriptionId){
        return new WSSubscription(sessionId, subscriptionId, "/test/destination");
    }

    /*
     * 입장, 퇴장, 일괄 퇴장시의 동시성 시나리오 테스트
     * 서로 다른 세션에서 입장 테스트
     *
     */

    @Nested
    @DisplayName("동시성 시나리오 테스트")
    class ConcurrentTest {



        @Test
        @DisplayName("동일 컨텐츠에 동시 입장 시, 작업이 직렬화 된다.")
        void concurrentJoin() throws Exception {
            WSSubscription subscription1 = subscription("session1", "subscription1");
            WSSubscription subscription2 = subscription("session2", "subscription2");

            String topic = "prefix-" + CONTENT_ID;
            roomStub(CONTENT_ID);

            CountDownLatch firstEntry = new CountDownLatch(1);
            CountDownLatch firstRelease = new CountDownLatch(1);
            CountDownLatch secondEntry = new CountDownLatch(1);

            Object lock = locks.lock(CONTENT_ID);

            // 특정 매서드가 호출되면 시행될 콜백.
            doAnswer(inv -> {
                firstEntry.countDown();
                firstRelease.await();
                return null;
            }).when(room).join(subscription1);

            AtomicReference<Thread> firstThread =
                    new AtomicReference<>();

            AtomicReference<Thread> secondThread =
                    new AtomicReference<>();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            Runnable runner1 = () -> chattingSessionService.join(subscription1, CONTENT_ID);
            Runnable runner2 = () -> chattingSessionService.join(subscription2, CONTENT_ID);

            try {
                // run!
                Future<?> first = executor.submit(() -> {
                    firstThread.set(Thread.currentThread());
                    runner1.run();
                });
                // until locked
                await(firstEntry);

                Future<?> second = executor.submit(() -> {
                    secondThread.set(Thread.currentThread());
                    secondEntry.countDown();
                    runner2.run();
                });
                // run2! -> still locked
                await(secondEntry);

                // 이 시점이 first 에만 락이 걸려있는 상황
                // 락 검정
                validateLock(
                        firstThread.get(),
                        secondThread.get(),
                        lock
                );

                assertThat(first.isDone())
                        .as("첫 번째 작업은 아직 완료되지 않아야 한다.")
                        .isFalse();

                assertThat(second.isDone())
                        .as("두 번째 작업은 락을 대기해야 한다.")
                        .isFalse();

                verify(room, never()).join(subscription2);
                verifyNoInteractions(
                        redisTopicRegistry
                );

                // first lock 로직 진행. (해제)
                firstRelease.countDown();

            } finally {
                executor.shutdown();
            }

            InOrder inOrder = inOrder(
                    chattingSessionRepository,
                    room,
                    redisTopicRegistry
            );

            // 첫 서비스 스레드
            inOrder.verify(chattingSessionRepository).findOrCreate(CONTENT_ID);
            inOrder.verify(room).join(subscription1);
            inOrder.verify(redisTopicRegistry).register(
                    topic,
                    subscription1.sessionId(),
                    subscription1.id(),
                    chattingSessionMessageListener
            );

            // 블록된 서비스 스레드
            inOrder.verify(chattingSessionRepository).findOrCreate(CONTENT_ID);
            inOrder.verify(room).join(subscription2);
            inOrder.verify(redisTopicRegistry).register(
                    topic,
                    subscription2.sessionId(),
                    subscription2.id(),
                    chattingSessionMessageListener
            );

        }

    }

    void roomStub(UUID contentId){
        when(chattingSessionRepository.findOrCreate(CONTENT_ID))
                .thenReturn(room);

        lenient().when(room.topic())
                .thenReturn("prefix-" + CONTENT_ID);
    }

    // 유틸리티
    void await(CountDownLatch cd) throws InterruptedException {
        assertThat(cd.await(TIME_OUT,TimeUnit.SECONDS))
                .as("Time out")
                .isTrue();
    }

    void validateLock(
            Thread blockedThread,
            Thread ownerThread,
            Object expectedLock
    ) throws InterruptedException {

        long out = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIME_OUT);

        while(System.nanoTime() < out) {
            ThreadInfo threadInfo = ManagementFactory   // 스레드 정보를 반환하는 팩토리 객체
                    .getThreadMXBean()  // 스레드 메트릭 정보를 제공하는 빈
                    .getThreadInfo(blockedThread.getId());

            if (
                    threadInfo != null
                            && threadInfo.getThreadState() == Thread.State.BLOCKED
                            // 락을 가지고 있는게 의도한 스레드인지 확인
                            && threadInfo.getLockOwnerId() == ownerThread.getId()
                            // 락 객체의 검증
                            && threadInfo.getLockInfo() != null
                            && threadInfo.getLockInfo().getIdentityHashCode() == System.identityHashCode(expectedLock)
            ) {
                break; // 통과 로직
            }
            Thread.sleep(10);
        }
    }


}
