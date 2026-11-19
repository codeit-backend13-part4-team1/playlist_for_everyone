package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service.view;

import com.codeit.sb13.playlist_for_everyone.global.service.lock.SpreadLock;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing.ViewingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("동시 시청 도메인의 시청 기능 테스트")
public class ViewingSessionServiceTest {

    @Mock
    ViewingSessionRepository viewingSessionRepository;

    @Mock
    ViewSessionMessageListener viewSessionMessageListener;

    // TODO - repository 레이어로 변경 예정
    @Mock
    WatchingSessionRepository watchingSessionRepository;


    @Mock
    RedisTopicRegistry redisTopicRegistry;

    // TODO - message 전송 레이어로 변경 예정
    @Mock
    RedisTemplate<String, Object> redisTemplate;

    // lock 구현체 주입
    @Spy
    SpreadLock locks;

    @InjectMocks
    ViewingSessionServiceImpl viewingSessionService;

    @Mock
    ViewingSessionRoom room;

    ExecutorService executor;

    UUID contentId;

    private static final long TIMEOUT_SECONDS = 1L;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        contentId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();

        assertThat(executor.awaitTermination(
                TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("테스트 스레드가 정상적으로 종료되어야 한다.")
                .isTrue();
    }

    // =========================================================
    // 동시성 테스트
    // =========================================================

    @Nested
    @DisplayName("동일 컨텐츠에 대한 동시성 테스트")
    class SameContentConcurrencyTest {

        @Test
        @DisplayName("동일 컨텐츠에 동시에 입장하면 입장 작업이 직렬화된다")
        void concurrentJoin() throws Exception {
            // given
            WSSubscription first =
                    createSubscription("session-1", "sub-1");

            WSSubscription second =
                    createSubscription("session-2", "sub-2");

            String topic = "topic-" + contentId;

            stubRoom(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            doAnswer(invocation -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }).when(room).join(first);

            // when
            executeSerialized(
                    () -> viewingSessionService.join(first, contentId),
                    () -> viewingSessionService.join(second, contentId),
                    firstEntered,
                    releaseFirst,
                    () -> {
                        verify(room, never()).join(second);

                        verifyNoInteractions(
                                watchingSessionRepository,
                                redisTopicRegistry
                        );
                    }
            );

            // then
            InOrder inOrder = inOrder(
                    viewingSessionRepository,
                    room,
                    watchingSessionRepository,
                    redisTopicRegistry
            );

            // 첫 번째 입장 작업
            inOrder.verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            inOrder.verify(room).join(first);

            inOrder.verify(watchingSessionRepository).register(
                    "session-1",
                    "sub-1",
                    contentId.toString()
            );

            inOrder.verify(redisTopicRegistry).register(
                    topic,
                    "session-1",
                    "sub-1",
                    viewSessionMessageListener
            );

            // 두 번째 입장 작업
            inOrder.verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            inOrder.verify(room).join(second);

            inOrder.verify(watchingSessionRepository).register(
                    "session-2",
                    "sub-2",
                    contentId.toString()
            );

            inOrder.verify(redisTopicRegistry).register(
                    topic,
                    "session-2",
                    "sub-2",
                    viewSessionMessageListener
            );
        }

        @Test
        @DisplayName("동일 컨텐츠의 입장 작업 중에는 퇴장 작업이 개입할 수 없다")
        void concurrentJoinAndLeave() throws Exception {
            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            String topic = "topic-" + contentId;

            stubRoom(contentId);
            stubExistingRoom(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            doAnswer(invocation -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }).when(room).join(subscription);

            // when
            executeSerialized(
                    () -> viewingSessionService.join(
                            subscription, contentId),

                    () -> viewingSessionService.leave(
                            subscription, contentId),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        verify(viewingSessionRepository, never())
                                .find(contentId);

                        verify(room, never())
                                .leave(subscription);

                        verify(watchingSessionRepository, never())
                                .unregister(anyString(), anyString());

                        verify(redisTopicRegistry, never())
                                .unregister(anyString(), anyString());
                    }
            );

            // then
            InOrder inOrder = inOrder(
                    viewingSessionRepository,
                    room,
                    watchingSessionRepository,
                    redisTopicRegistry
            );

            // 입장 작업
            inOrder.verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            inOrder.verify(room).join(subscription);

            inOrder.verify(watchingSessionRepository).register(
                    "session-1",
                    "sub-1",
                    contentId.toString()
            );

            inOrder.verify(redisTopicRegistry).register(
                    topic,
                    "session-1",
                    "sub-1",
                    viewSessionMessageListener
            );

            // 퇴장 작업
            inOrder.verify(viewingSessionRepository)
                    .find(contentId);

            inOrder.verify(room).leave(subscription);

            inOrder.verify(watchingSessionRepository)
                    .unregister("session-1", "sub-1");

            inOrder.verify(redisTopicRegistry)
                    .unregister("session-1", "sub-1");
        }

        @Test
        @DisplayName("동일 컨텐츠의 일괄 퇴장 작업 중에는 입장 작업이 개입할 수 없다")
        void concurrentJoinAndLeaveAll() throws Exception {
            // given
            WSSubscription first =
                    createSubscription("session-1", "sub-1");

            WSSubscription second =
                    createSubscription("session-1", "sub-2");

            WSSubscription newcomer =
                    createSubscription("session-2", "sub-3");

            List<WSSubscription> subscriptions =
                    List.of(first, second);

            List<String> subscriptionIds =
                    List.of("sub-1", "sub-2");

            stubRoom(contentId);
            stubExistingRoom(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            doAnswer(invocation -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }).when(room).leaveAll(subscriptions);

            // when
            executeSerialized(
                    () -> viewingSessionService.leaveAll(
                            subscriptions, contentId),

                    () -> viewingSessionService.join(
                            newcomer, contentId),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        verify(room, never()).join(newcomer);

                        verify(viewingSessionRepository, never())
                                .findOrCreate(contentId);

                        verifyNoInteractions(
                                watchingSessionRepository,
                                redisTopicRegistry
                        );
                    }
            );

            // then
            InOrder inOrder = inOrder(
                    viewingSessionRepository,
                    room,
                    watchingSessionRepository,
                    redisTopicRegistry
            );

            // 일괄 퇴장 작업
            inOrder.verify(viewingSessionRepository)
                    .find(contentId);

            inOrder.verify(room).leaveAll(subscriptions);

            inOrder.verify(watchingSessionRepository)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds,
                            contentId
                    );

            inOrder.verify(redisTopicRegistry)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds
                    );

            // 입장 작업
            inOrder.verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            inOrder.verify(room).join(newcomer);

            inOrder.verify(watchingSessionRepository)
                    .register(
                            "session-2",
                            "sub-3",
                            contentId.toString()
                    );

            inOrder.verify(redisTopicRegistry)
                    .register(
                            "topic-" + contentId,
                            "session-2",
                            "sub-3",
                            viewSessionMessageListener
                    );
        }

        @Test
        @DisplayName("동일 컨텐츠의 입장 작업 중에는 룸 제거 작업이 개입할 수 없다")
        void concurrentJoinAndDestroy() throws Exception {
            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            stubRoom(contentId);
            stubExistingRoom(contentId);

            when(room.getAll())
                    .thenReturn(List.of(subscription));

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            AtomicReference<List<WSSubscription>> destroyed =
                    new AtomicReference<>();

            doAnswer(invocation -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }).when(room).join(subscription);

            // when
            executeSerialized(
                    () -> viewingSessionService.join(
                            subscription, contentId),

                    () -> destroyed.set(
                            viewingSessionService.destroy(contentId)),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        verify(viewingSessionRepository, never())
                                .find(contentId);

                        verify(watchingSessionRepository, never())
                                .deleteWithContentId(contentId);

                        verify(redisTopicRegistry, never())
                                .disconnectWithContentId(
                                        contentId.toString());

                        verify(viewingSessionRepository, never())
                                .remove(room);

                        verify(room, never()).clear();
                    }
            );

            // then
            InOrder inOrder = inOrder(
                    viewingSessionRepository,
                    room,
                    watchingSessionRepository,
                    redisTopicRegistry
            );

            // 입장 작업
            inOrder.verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            inOrder.verify(room).join(subscription);

            inOrder.verify(watchingSessionRepository)
                    .register(
                            "session-1",
                            "sub-1",
                            contentId.toString()
                    );

            inOrder.verify(redisTopicRegistry)
                    .register(
                            "topic-" + contentId,
                            "session-1",
                            "sub-1",
                            viewSessionMessageListener
                    );

            // 룸 제거 작업
            inOrder.verify(viewingSessionRepository)
                    .find(contentId);

            inOrder.verify(watchingSessionRepository)
                    .deleteWithContentId(contentId);

            inOrder.verify(redisTopicRegistry)
                    .disconnectWithContentId(contentId.toString());

            inOrder.verify(viewingSessionRepository)
                    .remove(room);

            inOrder.verify(room).getAll();
            inOrder.verify(room).clear();

            assertThat(destroyed.get())
                    .containsExactly(subscription);
        }
    }

    @Nested
    @DisplayName("서로 다른 컨텐츠에 대한 동시성 테스트")
    class DifferentContentConcurrencyTest {

        @Test
        @DisplayName("서로 다른 락을 사용하는 컨텐츠는 독립적으로 입장할 수 있다")
        void concurrentJoinDifferentContents() throws Exception {
            // given
            UUID firstContentId = contentId;
            UUID secondContentId = UUID.randomUUID();

            // Striped Lock을 사용하는 경우 락 충돌 가능성이 존재한다.
            Assumptions.assumeTrue(
                    locks.lock(firstContentId)
                            != locks.lock(secondContentId),
                    "두 컨텐츠가 동일한 락에 매핑되었다."
            );

            WSSubscription first =
                    createSubscription("session-1", "sub-1");

            WSSubscription second =
                    createSubscription("session-2", "sub-2");

            ViewingSessionRoom firstRoom =
                    mock(ViewingSessionRoom.class);

            ViewingSessionRoom secondRoom =
                    mock(ViewingSessionRoom.class);

            when(viewingSessionRepository.findOrCreate(firstContentId))
                    .thenReturn(firstRoom);

            when(viewingSessionRepository.findOrCreate(secondContentId))
                    .thenReturn(secondRoom);

            when(firstRoom.topic()).thenReturn("topic-1");
            when(secondRoom.topic()).thenReturn("topic-2");

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            doAnswer(invocation -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }).when(firstRoom).join(first);

            try {
                // when
                Future<?> firstFuture = executor.submit(
                        () -> viewingSessionService.join(
                                first, firstContentId)
                );

                await(firstEntered);

                Future<?> secondFuture = executor.submit(
                        () -> viewingSessionService.join(
                                second, secondContentId)
                );

                // then
                // 첫 번째 작업이 정지된 상태에서도
                // 두 번째 컨텐츠의 작업은 완료되어야 한다.
                secondFuture.get(
                        TIMEOUT_SECONDS, TimeUnit.SECONDS);

                assertThat(firstFuture.isDone()).isFalse();

                verify(secondRoom).join(second);

                verify(watchingSessionRepository)
                        .register(
                                "session-2",
                                "sub-2",
                                secondContentId.toString()
                        );

                verify(redisTopicRegistry)
                        .register(
                                "topic-2",
                                "session-2",
                                "sub-2",
                                viewSessionMessageListener
                        );

                verify(watchingSessionRepository, never())
                        .register(
                                "session-1",
                                "sub-1",
                                firstContentId.toString()
                        );

                verify(redisTopicRegistry, never())
                        .register(
                                "topic-1",
                                "session-1",
                                "sub-1",
                                viewSessionMessageListener
                        );

                // 첫 번째 작업 재개
                releaseFirst.countDown();

                firstFuture.get(
                        TIMEOUT_SECONDS, TimeUnit.SECONDS);

                verify(watchingSessionRepository)
                        .register(
                                "session-1",
                                "sub-1",
                                firstContentId.toString()
                        );

                verify(redisTopicRegistry)
                        .register(
                                "topic-1",
                                "session-1",
                                "sub-1",
                                viewSessionMessageListener
                        );

            } finally {
                releaseFirst.countDown();
            }
        }
    }

    @Nested
    @DisplayName("컨텐츠 락의 임계 영역 검증")
    class LockScopeTest {

        @Test
        @DisplayName("입장 과정의 모든 저장 작업은 컨텐츠 락으로 보호된다")
        void joinHoldsContentLock() {
            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object lock = locks.lock(contentId);

            stubRoom(contentId);

            when(viewingSessionRepository.findOrCreate(contentId))
                    .thenAnswer(invocation -> {
                        assertLockHeld(lock);
                        return room;
                    });

            Answer<Void> assertLockHeld = invocation -> {
                assertLockHeld(lock);
                return null;
            };

            doAnswer(assertLockHeld)
                    .when(room).join(subscription);

            doAnswer(assertLockHeld)
                    .when(watchingSessionRepository)
                    .register(
                            "session-1",
                            "sub-1",
                            contentId.toString()
                    );

            doAnswer(assertLockHeld)
                    .when(redisTopicRegistry)
                    .register(
                            "topic-" + contentId,
                            "session-1",
                            "sub-1",
                            viewSessionMessageListener
                    );

            // when
            viewingSessionService.join(subscription, contentId);

            // then
            verify(viewingSessionRepository)
                    .findOrCreate(contentId);

            verify(room).join(subscription);

            verify(watchingSessionRepository)
                    .register(
                            "session-1",
                            "sub-1",
                            contentId.toString()
                    );

            verify(redisTopicRegistry)
                    .register(
                            "topic-" + contentId,
                            "session-1",
                            "sub-1",
                            viewSessionMessageListener
                    );

            assertThat(Thread.holdsLock(lock)).isFalse();
        }

        @Test
        @DisplayName("퇴장 과정의 모든 삭제 작업은 컨텐츠 락으로 보호된다")
        void leaveHoldsContentLock() {
            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object lock = locks.lock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {
                        assertLockHeld(lock);
                        return Optional.of(room);
                    });

            Answer<Void> assertLockHeld = invocation -> {
                assertLockHeld(lock);
                return null;
            };

            doAnswer(assertLockHeld)
                    .when(room).leave(subscription);

            doAnswer(assertLockHeld)
                    .when(watchingSessionRepository)
                    .unregister("session-1", "sub-1");

            doAnswer(assertLockHeld)
                    .when(redisTopicRegistry)
                    .unregister("session-1", "sub-1");

            // when
            viewingSessionService.leave(subscription, contentId);

            // then
            verify(room).leave(subscription);

            verify(watchingSessionRepository)
                    .unregister("session-1", "sub-1");

            verify(redisTopicRegistry)
                    .unregister("session-1", "sub-1");

            assertThat(Thread.holdsLock(lock)).isFalse();
        }

        @Test
        @DisplayName("일괄 퇴장 과정의 모든 삭제 작업은 컨텐츠 락으로 보호된다")
        void leaveAllHoldsContentLock() {
            // given
            WSSubscription first =
                    createSubscription("session-1", "sub-1");

            WSSubscription second =
                    createSubscription("session-1", "sub-2");

            List<WSSubscription> subscriptions =
                    List.of(first, second);

            List<String> subscriptionIds =
                    List.of("sub-1", "sub-2");

            Object lock = locks.lock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {
                        assertLockHeld(lock);
                        return Optional.of(room);
                    });

            Answer<Void> assertLockHeld = invocation -> {
                assertLockHeld(lock);
                return null;
            };

            doAnswer(assertLockHeld)
                    .when(room).leaveAll(subscriptions);

            doAnswer(assertLockHeld)
                    .when(watchingSessionRepository)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds,
                            contentId
                    );

            doAnswer(assertLockHeld)
                    .when(redisTopicRegistry)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds
                    );

            // when
            viewingSessionService.leaveAll(
                    subscriptions, contentId);

            // then
            verify(room).leaveAll(subscriptions);

            verify(watchingSessionRepository)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds,
                            contentId
                    );

            verify(redisTopicRegistry)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds
                    );

            assertThat(Thread.holdsLock(lock)).isFalse();
        }

        @Test
        @DisplayName("룸 제거 과정의 모든 작업은 컨텐츠 락으로 보호된다")
        void destroyHoldsContentLock() {
            // given
            Object lock = locks.lock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {
                        assertLockHeld(lock);
                        return Optional.of(room);
                    });

            Answer<Void> assertLockHeld = invocation -> {
                assertLockHeld(lock);
                return null;
            };

            doAnswer(assertLockHeld)
                    .when(watchingSessionRepository)
                    .deleteWithContentId(contentId);

            doAnswer(assertLockHeld)
                    .when(redisTopicRegistry)
                    .disconnectWithContentId(contentId.toString());

            doAnswer(assertLockHeld)
                    .when(viewingSessionRepository)
                    .remove(room);

            when(room.getAll()).thenAnswer(invocation -> {
                assertLockHeld(lock);
                return List.of();
            });

            doAnswer(assertLockHeld)
                    .when(room).clear();

            // when
            viewingSessionService.destroy(contentId);

            // then
            verify(watchingSessionRepository)
                    .deleteWithContentId(contentId);

            verify(redisTopicRegistry)
                    .disconnectWithContentId(contentId.toString());

            verify(viewingSessionRepository).remove(room);

            verify(room).getAll();
            verify(room).clear();

            assertThat(Thread.holdsLock(lock)).isFalse();
        }
    }

    // =========================================================
    // 테스트 데이터 및 Mock 설정
    // =========================================================

    private WSSubscription createSubscription(
            String sessionId,
            String subscriptionId
    ) {
        WSSubscription subscription =
                mock(WSSubscription.class);

        // lenient - 불필요한 stubbing 을 제외해서 unnecessary stubbing 예외를 일으키지 않게 한다.
        lenient().when(subscription.sessionId())
                .thenReturn(sessionId);

        lenient().when(subscription.id())
                .thenReturn(subscriptionId);

        return subscription;
    }

    private void stubRoom(UUID contentId) {
        when(viewingSessionRepository.findOrCreate(contentId))
                .thenReturn(room);

        when(room.topic())
                .thenReturn("topic-" + contentId);
    }

    private void stubExistingRoom(UUID contentId) {
        when(viewingSessionRepository.find(contentId))
                .thenReturn(Optional.of(room));
    }

    private void assertLockHeld(Object lock) {
        // Thread.holdsLock - 현재 스레드가 주어진 객체에 대한 모니터락을 보유하고 있는지 확인하는 매서드.
        // synchronized() 의 내재적 락만 확인 할 수 있다.
        assertThat(Thread.holdsLock(lock))
                .as("현재 작업은 컨텐츠에 대한 락을 보유해야 한다.")
                .isTrue();
    }

    // =========================================================
    // 동시성 테스트 공통 유틸리티
    // =========================================================

    /**
     * Latch의 카운트가 0이 될 때까지 대기한다.
     *
     * 무한 대기를 방지하기 위해 타임아웃을 적용한다.
     */
    private void await(CountDownLatch latch)
            throws InterruptedException {

        assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("Latch 대기 시간이 초과되었다.")
                .isTrue();
    }

    /**
     * 두 번째 스레드가 첫 번째 스레드가 보유한
     * 특정 모니터 락을 대기하는지 확인한다.
     *
     * 단순 BLOCKED 상태뿐만 아니라
     * 락 객체와 락 소유 스레드도 확인한다.
     */
    private void awaitBlocked(
            Thread blockedThread,
            Thread ownerThread,
            Object expectedLock
    ) throws InterruptedException {

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {

            ThreadInfo threadInfo = ManagementFactory
                    .getThreadMXBean()
                    .getThreadInfo(blockedThread.getId());

            if (threadInfo != null
                    && threadInfo.getThreadState()
                    == Thread.State.BLOCKED
                    && threadInfo.getLockOwnerId()
                    == ownerThread.getId()
                    && threadInfo.getLockInfo() != null
                    && threadInfo.getLockInfo().getIdentityHashCode()
                    == System.identityHashCode(expectedLock)
                    && threadInfo.getLockInfo().getClassName()
                    .equals(expectedLock.getClass().getName())) {

                return;
            }

            Thread.sleep(1);
        }

        fail("두 번째 스레드가 예상한 컨텐츠 락을 대기하지 않는다.");
    }

    /**
     * 첫 번째 작업을 임계 영역에서 정지시킨 후
     * 두 번째 작업이 동일한 락을 대기하는지 확인한다.
     *
     * 실행 순서:
     *
     * 1. 첫 번째 스레드 실행
     * 2. 첫 번째 스레드의 임계 영역 진입 확인
     * 3. 두 번째 스레드 실행
     * 4. 두 번째 스레드의 BLOCKED 상태 및 대기 락 확인
     * 5. 첫 번째 작업이 진행 중인 상태에서 검증
     * 6. 첫 번째 작업 재개
     * 7. 두 작업의 정상 종료 확인
     */
    private void executeSerialized(
            Runnable firstTask,
            Runnable secondTask,
            CountDownLatch firstEntered,
            CountDownLatch releaseFirst,
            Runnable whileBlocked
    ) throws Exception {

        Object expectedLock = locks.lock(contentId);

        AtomicReference<Thread> firstThread =
                new AtomicReference<>();

        AtomicReference<Thread> secondThread =
                new AtomicReference<>();

        CountDownLatch secondStarted =
                new CountDownLatch(1);

        try {
            Future<?> firstFuture = executor.submit(() -> {
                firstThread.set(Thread.currentThread());
                firstTask.run();
            });

            // 첫 번째 작업의 임계 영역 진입 대기
            await(firstEntered);

            Future<?> secondFuture = executor.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();

                secondTask.run();
            });

            // 두 번째 작업 시작 대기
            await(secondStarted);

            // 두 번째 스레드가 첫 번째 스레드의 락을
            // 기다리는지 확인한다.
            awaitBlocked(
                    secondThread.get(),
                    firstThread.get(),
                    expectedLock
            );

            assertThat(firstFuture.isDone())
                    .as("첫 번째 작업은 아직 완료되지 않아야 한다.")
                    .isFalse();

            assertThat(secondFuture.isDone())
                    .as("두 번째 작업은 락을 대기해야 한다.")
                    .isFalse();

            // 첫 번째 작업이 진행 중인 상태에서 검증
            whileBlocked.run();

            // 첫 번째 작업 재개
            releaseFirst.countDown();

            // 두 작업의 정상 종료 및 스레드 내부 예외 확인
            firstFuture.get(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS);

            secondFuture.get(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } finally {
            // 테스트 실패 시에도 대기 중인 스레드를 해제한다.
            releaseFirst.countDown();
        }
    }
}
