package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service.view;

import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
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
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("동시 시청 도메인의 시청 기능 테스트")
public class ViewingSessionServiceTest {

    @Mock
    ViewingSessionRepository viewingSessionRepository;

    @Mock
    ViewSessionMessageListener viewSessionMessageListener;

    @Mock
    ViewingSessionRedisRepository viewingSessionRedisRepository;

    @Mock
    RedisTopicRegistry redisTopicRegistry;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    Locks locks;

    @Mock
    ViewingSessionRoom room;

    ViewingSessionServiceImpl viewingSessionService;

    ExecutorService executor;

    UUID contentId;

    /*
     * 컨텐츠 ID별로 동일한 락 객체를 반환하기 위한 저장소.
     *
     * SpreadLock 자체의 구현은 검증 대상에서 제외하고,
     * 서비스의 컨텐츠 단위 락 사용 정책만 검증한다.
     */
    Map<UUID, Object> lockContainer;

    private static final long TIMEOUT_SECONDS = 10L;

    @BeforeEach
    void setUp() {

        executor = Executors.newFixedThreadPool(2);

        contentId = UUID.randomUUID();

        lockContainer = new ConcurrentHashMap<>();

        viewingSessionService = new ViewingSessionServiceImpl(
                viewingSessionRepository,
                viewSessionMessageListener,
                viewingSessionRedisRepository,
                redisTopicRegistry,
                redisTemplate,
                locks
        );

        /*
         * 동일한 contentId는 동일한 모니터 락을 사용한다.
         *
         * SEND와 같이 락을 사용하지 않는 테스트에서도
         * 공통 설정을 사용할 수 있도록 lenient 적용.
         */
        lenient().when(locks.lock(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return getContentLock(id);
                });
    }

    @AfterEach
    void tearDown() throws InterruptedException {

        executor.shutdownNow();

        assertThat(executor.awaitTermination(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ))
                .as("테스트 스레드가 정상적으로 종료되어야 한다.")
                .isTrue();
    }

    // =========================================================
    // 1. 동일 컨텐츠에 대한 동시성 테스트
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

            stubRoom(contentId);

            Object lock = getContentLock(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                firstEntered.countDown();

                awaitRelease(releaseFirst);

                return null;

            }).when(room).join(first);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                secondEntered.countDown();

                return null;

            }).when(room).join(second);

            // when
            executeSerialized(
                    () -> viewingSessionService.join(
                            first, contentId),

                    () -> viewingSessionService.join(
                            second, contentId),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        assertThat(secondEntered.getCount())
                                .as("첫 번째 JOIN 진행 중 두 번째 JOIN은 진입할 수 없다.")
                                .isEqualTo(1L);
                    }
            );

            // then
            InOrder inOrder = createInOrder();

            // 첫 번째 JOIN
            verifyJoinOrder(
                    inOrder,
                    room,
                    first,
                    contentId
            );

            // 두 번째 JOIN
            verifyJoinOrder(
                    inOrder,
                    room,
                    second,
                    contentId
            );

            assertThat(secondEntered.getCount())
                    .isZero();

            verify(viewingSessionRepository, times(2))
                    .findOrCreate(contentId);

            verify(room, times(1)).join(first);
            verify(room, times(1)).join(second);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }

        @Test
        @DisplayName("동일 컨텐츠의 입장 작업 중에는 퇴장 작업이 개입할 수 없다")
        void concurrentJoinAndLeave() throws Exception {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            stubRoom(contentId);
            stubExistingRoom(contentId);

            Object lock = getContentLock(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch leaveEntered = new CountDownLatch(1);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                firstEntered.countDown();

                awaitRelease(releaseFirst);

                return null;

            }).when(room).join(subscription);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                leaveEntered.countDown();

                return null;

            }).when(room).leave(subscription);

            // when
            executeSerialized(
                    () -> viewingSessionService.join(
                            subscription, contentId),

                    () -> viewingSessionService.leave(
                            subscription, contentId),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        assertThat(leaveEntered.getCount())
                                .as("JOIN 진행 중 LEAVE는 진입할 수 없다.")
                                .isEqualTo(1L);
                    }
            );

            // then
            InOrder inOrder = createInOrder();

            // JOIN
            verifyJoinOrder(
                    inOrder,
                    room,
                    subscription,
                    contentId
            );

            // LEAVE
            verifyLeaveOrder(
                    inOrder,
                    room,
                    subscription,
                    contentId
            );

            assertThat(leaveEntered.getCount())
                    .isZero();

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
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

            stubRoom(contentId);
            stubExistingRoom(contentId);

            Object lock = getContentLock(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch joinEntered = new CountDownLatch(1);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                firstEntered.countDown();

                awaitRelease(releaseFirst);

                return null;

            }).when(room).leaveAll(subscriptions);

            doAnswer(invocation -> {

                assertLockHeld(lock);

                joinEntered.countDown();

                return null;

            }).when(room).join(newcomer);

            // when
            executeSerialized(
                    () -> viewingSessionService.leaveAll(
                            subscriptions, contentId),

                    () -> viewingSessionService.join(
                            newcomer, contentId),

                    firstEntered,
                    releaseFirst,

                    () -> {
                        assertThat(joinEntered.getCount())
                                .as("LEAVE ALL 진행 중 JOIN은 진입할 수 없다.")
                                .isEqualTo(1L);
                    }
            );

            // then
            InOrder inOrder = createInOrder();

            // LEAVE ALL
            verifyLeaveAllOrder(
                    inOrder,
                    room,
                    subscriptions,
                    contentId
            );

            // JOIN
            verifyJoinOrder(
                    inOrder,
                    room,
                    newcomer,
                    contentId
            );

            assertThat(joinEntered.getCount())
                    .isZero();

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }

        @Test
        @DisplayName("동일 컨텐츠의 입장 작업 중에는 룸 제거 작업이 개입할 수 없다")
        void concurrentJoinAndDestroy() throws Exception {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            stubRoom(contentId);

            Object lock = getContentLock(contentId);

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch destroyEntered = new CountDownLatch(1);

            AtomicReference<List<WSSubscription>> destroyed =
                    new AtomicReference<>();

            /*
             * DESTROY의 첫 번째 작업은 find()이다.
             *
             * 해당 메서드에 진입하는 시점을 확인한다.
             */
            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        destroyEntered.countDown();

                        return Optional.of(room);
                    });

            when(room.getAll())
                    .thenReturn(List.of(subscription));

            doAnswer(invocation -> {

                assertLockHeld(lock);

                firstEntered.countDown();

                awaitRelease(releaseFirst);

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
                        assertThat(destroyEntered.getCount())
                                .as("JOIN 진행 중 DESTROY는 진입할 수 없다.")
                                .isEqualTo(1L);

                        assertThat(destroyed.get())
                                .isNull();
                    }
            );

            // then
            InOrder inOrder = createInOrder();

            // JOIN
            verifyJoinOrder(
                    inOrder,
                    room,
                    subscription,
                    contentId
            );

            // DESTROY
            verifyDestroyOrder(
                    inOrder,
                    room,
                    contentId
            );

            assertThat(destroyEntered.getCount())
                    .isZero();

            assertThat(destroyed.get())
                    .containsExactly(subscription);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }
    }

    // =========================================================
    // 2. 서로 다른 컨텐츠에 대한 동시성 테스트
    // =========================================================

    @Nested
    @DisplayName("서로 다른 컨텐츠에 대한 동시성 테스트")
    class DifferentContentConcurrencyTest {

        @Test
        @DisplayName("서로 다른 락을 사용하는 컨텐츠는 독립적으로 입장할 수 있다")
        void concurrentJoinDifferentContents() throws Exception {

            // given
            UUID firstContentId = contentId;
            UUID secondContentId = UUID.randomUUID();

            Object firstLock =
                    getContentLock(firstContentId);

            Object secondLock =
                    getContentLock(secondContentId);

            assertThat(firstLock)
                    .isNotSameAs(secondLock);

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

            when(firstRoom.topic())
                    .thenReturn(topic(firstContentId));

            when(secondRoom.topic())
                    .thenReturn(topic(secondContentId));

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            doAnswer(invocation -> {

                assertLockHeld(firstLock);

                firstEntered.countDown();

                awaitRelease(releaseFirst);

                return null;

            }).when(firstRoom).join(first);

            doAnswer(invocation -> {

                assertLockHeld(secondLock);

                assertThat(Thread.holdsLock(firstLock))
                        .as("두 번째 작업은 첫 번째 컨텐츠 락을 소유하면 안 된다.")
                        .isFalse();

                return null;

            }).when(secondRoom).join(second);

            try {

                // when
                Future<?> firstFuture = executor.submit(
                        () -> viewingSessionService.join(
                                first, firstContentId)
                );

                awaitEntry(
                        firstEntered,
                        firstFuture,
                        "첫 번째 컨텐츠 JOIN 진입"
                );

                Future<?> secondFuture = executor.submit(
                        () -> viewingSessionService.join(
                                second, secondContentId)
                );

                /*
                 * 첫 번째 작업이 락을 소유하고 있더라도
                 * 두 번째 컨텐츠 작업은 완료되어야 한다.
                 */
                awaitFuture(secondFuture);

                assertThat(firstFuture.isDone())
                        .as("첫 번째 작업은 아직 대기 중이어야 한다.")
                        .isFalse();

                assertThat(secondFuture.isDone())
                        .isTrue();

                /*
                 * 첫 번째 작업을 재개한다.
                 */
                releaseFirst.countDown();

                awaitFuture(firstFuture);

                // then
                InOrder inOrder = inOrder(
                        viewingSessionRepository,
                        viewingSessionRedisRepository,
                        redisTopicRegistry,
                        firstRoom,
                        secondRoom
                );

                verifyJoinOrder(
                        inOrder,
                        firstRoom,
                        first,
                        firstContentId
                );

                verifyJoinOrder(
                        inOrder,
                        secondRoom,
                        second,
                        secondContentId
                );

                verifyNoMoreInteractions(
                        viewingSessionRepository,
                        viewingSessionRedisRepository,
                        redisTopicRegistry,
                        firstRoom,
                        secondRoom
                );

            } finally {

                releaseFirst.countDown();
            }
        }
    }

    // =========================================================
    // 3. 컨텐츠 락의 임계 영역 검증
    // =========================================================

    @Nested
    @DisplayName("컨텐츠 락의 임계 영역 검증")
    class LockScopeTest {

        @Test
        @DisplayName("입장 과정의 모든 저장 작업은 컨텐츠 락으로 보호된다")
        void joinHoldsContentLock() {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object lock = getContentLock(contentId);

            when(viewingSessionRepository.findOrCreate(contentId))
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return room;
                    });

            when(room.topic())
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return topic(contentId);
                    });

            Answer<Void> answer = lockHeldAnswer(lock);

            doAnswer(answer)
                    .when(viewingSessionRedisRepository)
                    .register(
                            "session-1",
                            "sub-1",
                            contentId.toString()
                    );

            doAnswer(answer)
                    .when(redisTopicRegistry)
                    .register(
                            topic(contentId),
                            "session-1",
                            "sub-1",
                            viewSessionMessageListener
                    );

            doAnswer(answer)
                    .when(room).join(subscription);

            // when
            viewingSessionService.join(
                    subscription,
                    contentId
            );

            // then
            InOrder inOrder = createInOrder();

            verifyJoinOrder(
                    inOrder,
                    room,
                    subscription,
                    contentId
            );

            assertLockReleased(lock);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }

        @Test
        @DisplayName("퇴장 과정의 모든 삭제 작업은 컨텐츠 락으로 보호된다")
        void leaveHoldsContentLock() {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object lock = getContentLock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return Optional.of(room);
                    });

            Answer<Void> answer = lockHeldAnswer(lock);

            doAnswer(answer)
                    .when(room).leave(subscription);

            doAnswer(answer)
                    .when(viewingSessionRedisRepository)
                    .unregister("session-1", "sub-1");

            doAnswer(answer)
                    .when(redisTopicRegistry)
                    .unregister("session-1", "sub-1");

            // when
            viewingSessionService.leave(
                    subscription,
                    contentId
            );

            // then
            InOrder inOrder = createInOrder();

            verifyLeaveOrder(
                    inOrder,
                    room,
                    subscription,
                    contentId
            );

            assertLockReleased(lock);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
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

            Object lock = getContentLock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return Optional.of(room);
                    });

            Answer<Void> answer = lockHeldAnswer(lock);

            doAnswer(answer)
                    .when(room).leaveAll(subscriptions);

            doAnswer(answer)
                    .when(viewingSessionRedisRepository)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds,
                            contentId
                    );

            doAnswer(answer)
                    .when(redisTopicRegistry)
                    .unregisterAll(
                            "session-1",
                            subscriptionIds
                    );

            // when
            viewingSessionService.leaveAll(
                    subscriptions,
                    contentId
            );

            // then
            InOrder inOrder = createInOrder();

            verifyLeaveAllOrder(
                    inOrder,
                    room,
                    subscriptions,
                    contentId
            );

            assertLockReleased(lock);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }

        @Test
        @DisplayName("룸 제거 과정의 모든 작업은 컨텐츠 락으로 보호된다")
        void destroyHoldsContentLock() {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object lock = getContentLock(contentId);

            when(viewingSessionRepository.find(contentId))
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return Optional.of(room);
                    });

            Answer<Void> answer = lockHeldAnswer(lock);

            doAnswer(answer)
                    .when(viewingSessionRedisRepository)
                    .deleteWithContentId(contentId);

            doAnswer(answer)
                    .when(redisTopicRegistry)
                    .disconnectWithContentId(
                            contentId.toString()
                    );

            doAnswer(answer)
                    .when(viewingSessionRepository)
                    .remove(room);

            when(room.getAll())
                    .thenAnswer(invocation -> {

                        assertLockHeld(lock);

                        return List.of(subscription);
                    });

            doAnswer(answer)
                    .when(room).clear();

            // when
            List<WSSubscription> result =
                    viewingSessionService.destroy(contentId);

            // then
            InOrder inOrder = createInOrder();

            verifyDestroyOrder(
                    inOrder,
                    room,
                    contentId
            );

            assertThat(result)
                    .containsExactly(subscription);

            assertLockReleased(lock);

            verifyNoMoreInteractions(
                    viewingSessionRepository,
                    viewingSessionRedisRepository,
                    redisTopicRegistry,
                    room
            );
        }
    }

    // =========================================================
    // 4. 메시지 전송과 컨텐츠 락의 독립성
    // =========================================================

    @Nested
    @DisplayName("메시지 전송의 동시성 테스트")
    class SendConcurrencyTest {

        @Test
        @DisplayName("동일 컨텐츠의 JOIN 작업 중에도 메시지 전송이 가능하다")
        void sendDuringJoin() throws Exception {

            // given
            WSSubscription subscription =
                    createSubscription("session-1", "sub-1");

            Object payload = new Object();

            Object lock = getContentLock(contentId);

            stubRoom(contentId);
            stubExistingRoom(contentId);

            CountDownLatch joinEntered = new CountDownLatch(1);
            CountDownLatch releaseJoin = new CountDownLatch(1);
            CountDownLatch sendEntered = new CountDownLatch(1);

            /*
             * JOIN은 컨텐츠 락을 소유한 상태로 대기한다.
             */
            doAnswer(invocation -> {

                assertLockHeld(lock);

                joinEntered.countDown();

                awaitRelease(releaseJoin);

                return null;

            }).when(room).join(subscription);

            /*
             * 메시지 전송 작업은 컨텐츠 락을
             * 소유하지 않아야 한다.
             */
            doAnswer(invocation -> {

                assertLockReleased(lock);

                sendEntered.countDown();

                return 1L;

            }).when(redisTemplate).convertAndSend(
                    topic(contentId),
                    payload
            );

            try {

                // when
                Future<?> joinFuture = executor.submit(
                        () -> viewingSessionService.join(
                                subscription,
                                contentId
                        )
                );

                awaitEntry(
                        joinEntered,
                        joinFuture,
                        "JOIN 작업 진입"
                );

                Future<?> sendFuture = executor.submit(
                        () -> viewingSessionService.send(
                                contentId,
                                payload
                        )
                );

                /*
                 * JOIN 작업이 컨텐츠 락을 소유하고 있어도
                 * SEND 작업은 완료될 수 있어야 한다.
                 */
                awaitFuture(sendFuture);

                assertThat(joinFuture.isDone())
                        .as("JOIN 작업은 아직 대기 중이어야 한다.")
                        .isFalse();

                assertThat(sendFuture.isDone())
                        .isTrue();

                assertThat(sendEntered.getCount())
                        .as("메시지 전송 작업이 실행되어야 한다.")
                        .isZero();

                // JOIN 재개
                releaseJoin.countDown();

                awaitFuture(joinFuture);

                // then
                verify(room).join(subscription);

                verify(viewingSessionRepository)
                        .findOrCreate(contentId);

                verify(viewingSessionRepository)
                        .find(contentId);

                verify(redisTemplate, times(1))
                        .convertAndSend(
                                topic(contentId),
                                payload
                        );

                verifyNoMoreInteractions(redisTemplate);

            } finally {

                releaseJoin.countDown();
            }
        }

        @Test
        @DisplayName("동일 컨텐츠에 대한 두 메시지 전송은 동시에 실행될 수 있다")
        void concurrentSend() throws Exception {

            // given
            Object firstPayload = new Object();
            Object secondPayload = new Object();

            Object lock = getContentLock(contentId);

            stubExistingRoom(contentId);

            when(room.topic())
                    .thenReturn(topic(contentId));

            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            CountDownLatch secondEntered = new CountDownLatch(1);
            CountDownLatch releaseSecond = new CountDownLatch(1);

            AtomicInteger activeSendCount = new AtomicInteger();
            AtomicInteger maxActiveSendCount = new AtomicInteger();

            /*
             * 첫 번째 메시지 전송을 중단시킨다.
             */
            doAnswer(invocation -> {

                assertLockReleased(lock);

                int active = activeSendCount.incrementAndGet();

                maxActiveSendCount.accumulateAndGet(
                        active,
                        Math::max
                );

                try {

                    firstEntered.countDown();

                    awaitRelease(releaseFirst);

                    return 1L;

                } finally {

                    activeSendCount.decrementAndGet();
                }

            }).when(redisTemplate).convertAndSend(
                    topic(contentId),
                    firstPayload
            );

            /*
             * 첫 번째 메시지 전송이 진행 중이어도
             * 두 번째 메시지 전송에 진입할 수 있어야 한다.
             */
            doAnswer(invocation -> {

                assertLockReleased(lock);

                int active = activeSendCount.incrementAndGet();

                maxActiveSendCount.accumulateAndGet(
                        active,
                        Math::max
                );

                try {

                    secondEntered.countDown();

                    awaitRelease(releaseSecond);

                    return 1L;

                } finally {

                    activeSendCount.decrementAndGet();
                }

            }).when(redisTemplate).convertAndSend(
                    topic(contentId),
                    secondPayload
            );

            try {

                // when
                Future<?> firstFuture = executor.submit(
                        () -> viewingSessionService.send(
                                contentId,
                                firstPayload
                        )
                );

                awaitEntry(
                        firstEntered,
                        firstFuture,
                        "첫 번째 SEND 진입"
                );

                Future<?> secondFuture = executor.submit(
                        () -> viewingSessionService.send(
                                contentId,
                                secondPayload
                        )
                );

                awaitEntry(
                        secondEntered,
                        secondFuture,
                        "두 번째 SEND 진입"
                );

                // then
                assertThat(firstFuture.isDone())
                        .isFalse();

                assertThat(secondFuture.isDone())
                        .isFalse();

                assertThat(activeSendCount.get())
                        .as("두 메시지 전송이 동시에 실행되어야 한다.")
                        .isEqualTo(2);

                assertThat(maxActiveSendCount.get())
                        .isEqualTo(2);

                /*
                 * 두 번째 메시지 전송만 먼저 완료한다.
                 */
                releaseSecond.countDown();

                awaitFuture(secondFuture);

                assertThat(firstFuture.isDone())
                        .as("두 번째 SEND는 첫 번째 SEND의 완료에 의존하지 않는다.")
                        .isFalse();

                assertThat(activeSendCount.get())
                        .isEqualTo(1);

                /*
                 * 첫 번째 메시지 전송도 완료한다.
                 */
                releaseFirst.countDown();

                awaitFuture(firstFuture);

                assertThat(activeSendCount.get())
                        .isZero();

                assertThat(maxActiveSendCount.get())
                        .isEqualTo(2);

                verify(viewingSessionRepository, times(2))
                        .find(contentId);

                verify(redisTemplate, times(1))
                        .convertAndSend(
                                topic(contentId),
                                firstPayload
                        );

                verify(redisTemplate, times(1))
                        .convertAndSend(
                                topic(contentId),
                                secondPayload
                        );

                verifyNoMoreInteractions(redisTemplate);

                /*
                 * SEND 자체는 Locks에 접근하지 않아야 한다.
                 */
                verifyNoInteractions(locks);

                verifyNoInteractions(
                        viewingSessionRedisRepository,
                        redisTopicRegistry
                );

            } finally {

                releaseFirst.countDown();
                releaseSecond.countDown();
            }
        }
    }

    // =========================================================
    // 테스트 데이터 및 Mock 설정
    // =========================================================

    /**
     * 테스트용 구독 객체 생성.
     *
     * 테스트 대상은 구독 객체의 동작이 아닌
     * 서비스의 동시성 정책이므로 Mock으로 관리한다.
     */
    private WSSubscription createSubscription(
            String sessionId,
            String subscriptionId
    ) {

        WSSubscription subscription =
                mock(WSSubscription.class);

        lenient().when(subscription.sessionId())
                .thenReturn(sessionId);

        lenient().when(subscription.id())
                .thenReturn(subscriptionId);

        return subscription;
    }

    /**
     * JOIN 작업에 필요한 Stub.
     */
    private void stubRoom(UUID contentId) {

        when(viewingSessionRepository.findOrCreate(contentId))
                .thenReturn(room);

        when(room.topic())
                .thenReturn(topic(contentId));
    }

    /**
     * 기존 룸을 조회하는 작업에 필요한 Stub.
     */
    private void stubExistingRoom(UUID contentId) {

        when(viewingSessionRepository.find(contentId))
                .thenReturn(Optional.of(room));
    }

    /**
     * 컨텐츠 ID에 대응하는 테스트용 토픽.
     */
    private String topic(UUID contentId) {

        return "topic-" + contentId;
    }

    /**
     * 동일한 컨텐츠 ID는 항상 동일한 락 객체를 반환한다.
     *
     * 테스트 검증 과정에서는 locks Mock을 직접 호출하지 않고
     * 이 메서드를 통해 모니터 객체를 참조한다.
     */
    private Object getContentLock(UUID contentId) {

        return lockContainer.computeIfAbsent(
                contentId,
                key -> new Object()
        );
    }

    // =========================================================
    // 호출 순서 검증 공통 메서드
    // =========================================================

    private InOrder createInOrder() {

        return inOrder(
                viewingSessionRepository,
                viewingSessionRedisRepository,
                redisTopicRegistry,
                room
        );
    }

    /**
     * JOIN 호출 순서 검증.
     *
     * 1. 룸 조회 또는 생성
     * 2. Presence 등록
     * 3. Redis Topic 등록
     * 4. 로컬 룸 등록
     */
    private void verifyJoinOrder(
            InOrder inOrder,
            ViewingSessionRoom targetRoom,
            WSSubscription subscription,
            UUID contentId
    ) {

        inOrder.verify(viewingSessionRepository)
                .findOrCreate(contentId);

        inOrder.verify(viewingSessionRedisRepository)
                .register(
                        subscription.sessionId(),
                        subscription.id(),
                        contentId.toString()
                );

        inOrder.verify(targetRoom)
                .topic();

        inOrder.verify(redisTopicRegistry)
                .register(
                        topic(contentId),
                        subscription.sessionId(),
                        subscription.id(),
                        viewSessionMessageListener
                );

        inOrder.verify(targetRoom)
                .join(subscription);
    }

    /**
     * LEAVE 호출 순서 검증.
     *
     * 1. 룸 조회
     * 2. 로컬 룸 구독 해제
     * 3. Presence 등록 해제
     * 4. Redis Topic 등록 해제
     */
    private void verifyLeaveOrder(
            InOrder inOrder,
            ViewingSessionRoom targetRoom,
            WSSubscription subscription,
            UUID contentId
    ) {

        inOrder.verify(viewingSessionRepository)
                .find(contentId);

        inOrder.verify(targetRoom)
                .leave(subscription);

        inOrder.verify(viewingSessionRedisRepository)
                .unregister(
                        subscription.sessionId(),
                        subscription.id()
                );

        inOrder.verify(redisTopicRegistry)
                .unregister(
                        subscription.sessionId(),
                        subscription.id()
                );
    }

    /**
     * LEAVE ALL 호출 순서 검증.
     *
     * 1. 룸 조회
     * 2. 로컬 룸 일괄 구독 해제
     * 3. Presence 일괄 등록 해제
     * 4. Redis Topic 일괄 등록 해제
     */
    private void verifyLeaveAllOrder(
            InOrder inOrder,
            ViewingSessionRoom targetRoom,
            List<WSSubscription> subscriptions,
            UUID contentId
    ) {

        String sessionId =
                subscriptions.get(0).sessionId();

        List<String> subscriptionIds =
                subscriptions.stream()
                        .map(WSSubscription::id)
                        .toList();

        inOrder.verify(viewingSessionRepository)
                .find(contentId);

        inOrder.verify(targetRoom)
                .leaveAll(subscriptions);

        inOrder.verify(viewingSessionRedisRepository)
                .unregisterAll(
                        sessionId,
                        subscriptionIds,
                        contentId
                );

        inOrder.verify(redisTopicRegistry)
                .unregisterAll(
                        sessionId,
                        subscriptionIds
                );
    }

    /**
     * DESTROY 호출 순서 검증.
     *
     * 1. 룸 조회
     * 2. Presence 삭제
     * 3. Redis Topic 연결 해제
     * 4. Repository에서 룸 제거
     * 5. 반환용 구독 객체 조회
     * 6. 룸 초기화
     */
    private void verifyDestroyOrder(
            InOrder inOrder,
            ViewingSessionRoom targetRoom,
            UUID contentId
    ) {

        inOrder.verify(viewingSessionRepository)
                .find(contentId);

        inOrder.verify(viewingSessionRedisRepository)
                .deleteWithContentId(contentId);

        inOrder.verify(redisTopicRegistry)
                .disconnectWithContentId(
                        contentId.toString()
                );

        inOrder.verify(viewingSessionRepository)
                .remove(targetRoom);

        inOrder.verify(targetRoom)
                .getAll();

        inOrder.verify(targetRoom)
                .clear();
    }

    // =========================================================
    // Lock 검증 공통 메서드
    // =========================================================

    /**
     * 현재 스레드가 해당 컨텐츠 락을 소유하는지 검증한다.
     */
    private void assertLockHeld(Object lock) {

        assertThat(Thread.holdsLock(lock))
                .as("현재 작업은 컨텐츠 락을 소유해야 한다.")
                .isTrue();
    }

    /**
     * 현재 스레드가 해당 컨텐츠 락을 소유하지 않는지 검증한다.
     */
    private void assertLockReleased(Object lock) {

        assertThat(Thread.holdsLock(lock))
                .as("현재 스레드는 컨텐츠 락을 소유하면 안 된다.")
                .isFalse();
    }

    /**
     * void 메서드의 임계 영역 검증을 위한 공통 Answer.
     */
    private Answer<Void> lockHeldAnswer(Object lock) {

        return invocation -> {

            assertLockHeld(lock);

            return null;
        };
    }

    // =========================================================
    // 동시성 테스트 공통 유틸리티
    // =========================================================

    /**
     * 작업 스레드의 실행을 중단한다.
     *
     * 작업 스레드에는 자체 타임아웃을 적용하지 않는다.
     * 테스트 스레드가 명시적으로 해제할 때까지 대기한다.
     *
     * 반드시 호출부의 finally에서 latch를 해제해야 한다.
     */
    private void awaitRelease(CountDownLatch latch) {

        try {

            latch.await();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new AssertionError(
                    "작업 스레드 대기 중 인터럽트가 발생했다.",
                    e
            );
        }
    }

    /**
     * 테스트 스레드에서 특정 작업의 진입 시점을 기다린다.
     */
    private void await(CountDownLatch latch)
            throws InterruptedException {

        assertThat(latch.await(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        ))
                .as("Latch 대기 시간이 초과되었다.")
                .isTrue();
    }

    /**
     * 작업 스레드가 특정 지점에 진입하는지 확인한다.
     *
     * Latch 진입 전에 작업이 실패하면 Future를 통해
     * 작업 스레드에서 발생한 예외를 즉시 확인한다.
     */
    private void awaitEntry(
            CountDownLatch latch,
            Future<?> future,
            String description
    ) throws Exception {

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {

            if (latch.await(10, TimeUnit.MILLISECONDS)) {

                return;
            }

            if (future.isDone()) {

                /*
                 * 작업 중 발생한 예외가 있으면 여기서 전파된다.
                 */
                awaitFuture(future);

                fail(description + " 전에 작업이 먼저 종료되었다.");
            }
        }

        fail(description + " - 대기 시간이 초과되었다.");
    }

    /**
     * 작업의 정상 종료 여부를 확인한다.
     *
     * 작업 스레드에서 발생한 예외도 함께 검증한다.
     */
    private void awaitFuture(Future<?> future)
            throws Exception {

        future.get(
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * 두 번째 스레드가 첫 번째 스레드가 보유한
     * 특정 모니터 락을 대기하는지 확인한다.
     *
     * BLOCKED 상태뿐 아니라 실제 락 소유자 및
     * 대기 대상 모니터 객체를 함께 검증한다.
     */
    private void awaitBlocked(
            Thread blockedThread,
            Thread ownerThread,
            Object expectedLock,
            Future<?> blockedFuture
    ) throws Exception {

        ThreadMXBean threadMXBean =
                ManagementFactory.getThreadMXBean();

        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {

            /*
             * 락을 대기해야 하는 작업이 먼저 완료되었다면
             * 해당 작업의 예외를 확인한다.
             */
            if (blockedFuture.isDone()) {

                awaitFuture(blockedFuture);

                fail("락을 대기해야 하는 작업이 먼저 종료되었다.");
            }

            ThreadInfo threadInfo =
                    threadMXBean.getThreadInfo(
                            blockedThread.getId()
                    );

            if (threadInfo != null
                    && threadInfo.getThreadState()
                    == Thread.State.BLOCKED
                    && threadInfo.getLockOwnerId()
                    == ownerThread.getId()
                    && threadInfo.getLockInfo() != null
                    && threadInfo.getLockInfo()
                    .getIdentityHashCode()
                    == System.identityHashCode(expectedLock)
                    && threadInfo.getLockInfo()
                    .getClassName()
                    .equals(expectedLock.getClass().getName())) {

                return;
            }

            /*
             * Busy Waiting 방지.
             *
             * 작업 순서를 결정하기 위한 sleep이 아니라
             * 스레드 상태 확인 간격을 조절하기 위한 대기이다.
             */
            LockSupport.parkNanos(
                    TimeUnit.MILLISECONDS.toNanos(1)
            );

            if (Thread.currentThread().isInterrupted()) {

                throw new InterruptedException(
                        "모니터 락 대기 상태 검증 중 인터럽트 발생"
                );
            }
        }

        fail(
                "두 번째 스레드가 예상한 컨텐츠 락을 대기하지 않는다."
                        + " 현재 상태: "
                        + blockedThread.getState()
        );
    }

    /**
     * 동일 컨텐츠에 대한 두 작업의 직렬화를 검증한다.
     *
     * 실행 순서:
     *
     * 1. 첫 번째 작업 실행
     * 2. 첫 번째 작업의 임계 영역 진입 확인
     * 3. 두 번째 작업 실행
     * 4. 두 번째 작업의 BLOCKED 상태 확인
     * 5. 락 대기 중 외부 상태 검증
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

        Object expectedLock =
                getContentLock(contentId);

        AtomicReference<Thread> firstThread =
                new AtomicReference<>();

        AtomicReference<Thread> secondThread =
                new AtomicReference<>();

        CountDownLatch secondStarted =
                new CountDownLatch(1);

        try {

            /*
             * 첫 번째 작업 실행.
             */
            Future<?> firstFuture = executor.submit(() -> {

                firstThread.set(Thread.currentThread());

                firstTask.run();
            });

            awaitEntry(
                    firstEntered,
                    firstFuture,
                    "첫 번째 작업의 임계 영역 진입"
            );

            /*
             * 첫 번째 작업이 임계 영역에 있는 동안
             * 두 번째 작업 실행.
             */
            Future<?> secondFuture = executor.submit(() -> {

                secondThread.set(Thread.currentThread());

                secondStarted.countDown();

                secondTask.run();
            });

            await(secondStarted);

            /*
             * 두 번째 작업이 동일한 모니터 락을
             * 대기하고 있는지 확인한다.
             */
            awaitBlocked(
                    secondThread.get(),
                    firstThread.get(),
                    expectedLock,
                    secondFuture
            );

            assertThat(firstFuture.isDone())
                    .as("첫 번째 작업은 아직 완료되지 않아야 한다.")
                    .isFalse();

            assertThat(secondFuture.isDone())
                    .as("두 번째 작업은 락을 대기해야 한다.")
                    .isFalse();

            /*
             * 작업 스레드가 락을 소유하고 있는 동안
             * Mockito verify를 직접 수행하지 않는다.
             *
             * CountDownLatch, AtomicReference 등의
             * 외부 상태 변수만 확인한다.
             */
            whileBlocked.run();

            /*
             * 첫 번째 작업을 재개시킨다.
             */
            releaseFirst.countDown();

            /*
             * Future를 통해 모든 작업의 정상 종료를 확인한다.
             */
            awaitFuture(firstFuture);
            awaitFuture(secondFuture);

        } finally {

            /*
             * 테스트 실패 시에도 대기 중인 작업 스레드를
             * 반드시 해제한다.
             */
            releaseFirst.countDown();
        }
    }
}