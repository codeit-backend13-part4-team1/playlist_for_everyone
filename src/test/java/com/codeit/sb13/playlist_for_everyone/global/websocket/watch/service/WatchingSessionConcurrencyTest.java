package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSession;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketSessionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WatchingSessionMapper;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.Watcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionServiceImpl;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.chatting.ChattingSessionService;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WatchingSession 서비스 동시성 테스트.
 *
 * 1. 동일 세션의 JOIN 작업 직렬화
 * 2. JOIN 작업 중 LEAVE 작업 대기
 * 3. Viewing 메시지 전송과 Session Lock의 독립성
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WatchingSession 테스트")
public class WatchingSessionConcurrencyTest {

    private static final long TIMEOUT_SECONDS = 10L;

    @Mock
    WebSocketSessionRegistry sessionRegistry;

    @Mock
    UserService userService;

    @Mock
    ContentService contentService;

    @Mock
    ViewingSessionService viewingSessionService;

    @Mock
    ChattingSessionService chattingSessionService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    WatchingSessionMapper mapper;

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    SecurityUser auth;

    WatchingSessionService watchingSessionService;

    private enum SessionType {
        VIEWING,
        CHATTING
    }

    private enum OperationType {
        JOIN,
        LEAVE
    }

    @BeforeEach
    void setUp() {
        setUpParam();
    }

    /**
     * 의존성 주입.
     */
    void setUpParam() {
        watchingSessionService = new WatchingSessionServiceImpl(
                sessionRegistry,
                viewingSessionService,
                chattingSessionService,
                userService,
                contentService,
                mapper,
                eventPublisher
        );
    }

    @Nested
    @DisplayName("동시성 시나리오 검증")
    class ConcurrencyScenarioTest {

        private ExecutorService executorService;

        private String sessionId;
        private UUID contentId;

        private WSSession session;

        /**
         * 테스트에서 사용하기 위해 준비한 구독 객체.
         */
        private Map<String, WSSubscription> preparedSubscriptions;

        /**
         * 실제 Mock Session에 등록된 구독 객체.
         */
        private Map<String, WSSubscription> subscriptions;

        /**
         * 구독 등록, 조회, 해제 작업의 호출 횟수.
         *
         * Lock을 소유한 작업 스레드가 존재하는 동안
         * Mockito verify를 호출하지 않기 위해 사용한다.
         */
        private AtomicInteger subscribeCount;
        private AtomicInteger getSubscriptionCount;
        private AtomicInteger unsubscribeCount;

        @BeforeEach
        void setUpConcurrency() {

            executorService = Executors.newFixedThreadPool(2);

            sessionId = "test-session-1";
            contentId = UUID.randomUUID();

            preparedSubscriptions = new ConcurrentHashMap<>();
            subscriptions = new ConcurrentHashMap<>();

            subscribeCount = new AtomicInteger();
            getSubscriptionCount = new AtomicInteger();
            unsubscribeCount = new AtomicInteger();

            session = sessionStub();

            sessionRepositoryStub(sessionId, session);
        }

        @AfterEach
        void tearDownConcurrency() throws InterruptedException {

            executorService.shutdownNow();

            assertTrue(
                    executorService.awaitTermination(
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    ),
                    "테스트 스레드가 정상적으로 종료되지 않았다."
            );
        }

        /*
         * ==========================================
         * 1. JOIN 작업의 직렬화 검증
         * ==========================================
         */

        @Nested
        @DisplayName("JOIN 작업의 직렬화 검증")
        class JoinSerializationTest {

            @Test
            @DisplayName("동일 세션의 Viewing JOIN 작업은 직렬화된다.")
            void joinViewingScenarioTest() throws Exception {

                verifyJoinSerialization(
                        SessionType.VIEWING,
                        SessionType.VIEWING
                );
            }

            @Test
            @DisplayName("동일 세션의 Chatting JOIN 작업은 직렬화된다.")
            void joinChattingScenarioTest() throws Exception {

                verifyJoinSerialization(
                        SessionType.CHATTING,
                        SessionType.CHATTING
                );
            }

            @Test
            @DisplayName("동일 세션의 Viewing JOIN과 Chatting JOIN은 직렬화된다.")
            void joinMixedScenarioTest() throws Exception {

                verifyJoinSerialization(
                        SessionType.VIEWING,
                        SessionType.CHATTING
                );
            }
        }

        /*
         * ==========================================
         * 2. JOIN 작업 중 LEAVE 작업의 대기 검증
         * ==========================================
         */

        @Nested
        @DisplayName("JOIN 작업 중 LEAVE 작업의 대기 검증")
        class JoinLeaveSerializationTest {

            @Test
            @DisplayName("Viewing JOIN이 진행 중이면 LEAVE 작업은 대기한다.")
            void joinAndLeaveViewingScenarioTest() throws Exception {

                verifyJoinAndLeaveSerialization(
                        SessionType.VIEWING
                );
            }

            @Test
            @DisplayName("Chatting JOIN이 진행 중이면 LEAVE 작업은 대기한다.")
            void joinAndLeaveChattingScenarioTest() throws Exception {

                verifyJoinAndLeaveSerialization(
                        SessionType.CHATTING
                );
            }
        }

        /*
         * ==========================================
         * 3. Viewing 메시지 전송 동시성 검증
         * ==========================================
         */

        @Nested
        @DisplayName("Viewing 메시지 전송의 동시성 검증")
        class ViewingMessageConcurrencyTest {

            @Test
            @DisplayName("JOIN 메시지 전송 중에도 다른 JOIN 작업이 가능하다.")
            void joinSendAndJoinScenarioTest() throws Exception {

                verifyViewingMessageConcurrency(
                        OperationType.JOIN,
                        OperationType.JOIN
                );
            }

            @Test
            @DisplayName("JOIN 메시지 전송 중에도 LEAVE 작업이 가능하다.")
            void joinSendAndLeaveScenarioTest() throws Exception {

                verifyViewingMessageConcurrency(
                        OperationType.JOIN,
                        OperationType.LEAVE
                );
            }

            @Test
            @DisplayName("LEAVE 메시지 전송 중에도 JOIN 작업이 가능하다.")
            void leaveSendAndJoinScenarioTest() throws Exception {

                verifyViewingMessageConcurrency(
                        OperationType.LEAVE,
                        OperationType.JOIN
                );
            }

            @Test
            @DisplayName("LEAVE 메시지 전송 중에도 다른 LEAVE 작업이 가능하다.")
            void leaveSendAndLeaveScenarioTest() throws Exception {

                verifyViewingMessageConcurrency(
                        OperationType.LEAVE,
                        OperationType.LEAVE
                );
            }
        }

        /*
         * ==========================================
         * JOIN 작업 직렬화 공통 검증 메서드
         * ==========================================
         */

        private void verifyJoinSerialization(
                SessionType firstType,
                SessionType secondType
        ) throws Exception {

            // given

            WSSubscription firstSubscription = subscriptionStub(
                    "subscription-1",
                    firstType
            );

            WSSubscription secondSubscription = subscriptionStub(
                    "subscription-2",
                    secondType
            );

            if (firstType == SessionType.VIEWING
                    || secondType == SessionType.VIEWING) {

                sendViewingMessageStub(contentId);
            }

            CountDownLatch firstJoinEntered = new CountDownLatch(1);
            CountDownLatch firstJoinRelease = new CountDownLatch(1);

            CountDownLatch secondJoinEntered = new CountDownLatch(1);

            AtomicReference<Thread> firstThread =
                    new AtomicReference<>();

            AtomicReference<Thread> secondThread =
                    new AtomicReference<>();

            AtomicInteger activeJoinCount = new AtomicInteger();
            AtomicInteger maxActiveJoinCount = new AtomicInteger();

            /*
             * 첫 번째 JOIN 작업.
             */
            joinServiceStub(
                    firstType,
                    firstSubscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "첫 번째 JOIN은 Session Lock을 소유해야 한다."
                        );

                        int active = activeJoinCount.incrementAndGet();

                        maxActiveJoinCount.accumulateAndGet(
                                active,
                                Math::max
                        );

                        try {

                            firstJoinEntered.countDown();

                            /*
                             * 테스트 스레드가 명시적으로 해제할 때까지 대기.
                             * 작업 스레드에는 자체 타임아웃을 설정하지 않는다.
                             */
                            awaitRelease(firstJoinRelease);

                            return null;

                        } finally {
                            activeJoinCount.decrementAndGet();
                        }
                    }
            );

            /*
             * 두 번째 JOIN 작업.
             */
            joinServiceStub(
                    secondType,
                    secondSubscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "두 번째 JOIN은 Session Lock을 소유해야 한다."
                        );

                        int active = activeJoinCount.incrementAndGet();

                        maxActiveJoinCount.accumulateAndGet(
                                active,
                                Math::max
                        );

                        try {

                            secondJoinEntered.countDown();

                            return null;

                        } finally {
                            activeJoinCount.decrementAndGet();
                        }
                    }
            );

            try {

                // when

                Future<?> firstFuture = executorService.submit(() -> {

                    firstThread.set(Thread.currentThread());

                    executeJoin(firstType, firstSubscription);
                });

                awaitLatch(
                        firstJoinEntered,
                        "첫 번째 JOIN 진입"
                );

                Future<?> secondFuture = executorService.submit(() -> {

                    secondThread.set(Thread.currentThread());

                    executeJoin(secondType, secondSubscription);
                });

                /*
                 * 두 번째 스레드가 실제 Session Lock을
                 * 대기하고 있는지 확인한다.
                 */
                awaitBlocked(
                        secondThread,
                        firstThread,
                        session,
                        secondFuture
                );

                // then - 첫 번째 JOIN 종료 전

                /*
                 * 주의:
                 * 이 구간에서는 session에 대한 Mockito verify를
                 * 호출하지 않는다.
                 */

                assertEquals(
                        1,
                        subscribeCount.get(),
                        "첫 번째 구독만 등록 작업에 진입해야 한다."
                );

                assertEquals(
                        1L,
                        secondJoinEntered.getCount(),
                        "두 번째 JOIN은 아직 진입하면 안 된다."
                );

                assertEquals(
                        1,
                        activeJoinCount.get()
                );

                assertFalse(firstFuture.isDone());

                assertFalse(secondFuture.isDone());

                assertSame(
                        firstSubscription,
                        subscriptions.get(firstSubscription.id())
                );

                assertFalse(
                        subscriptions.containsKey(secondSubscription.id()),
                        "두 번째 구독은 아직 등록되면 안 된다."
                );

                /*
                 * 첫 번째 JOIN 작업을 재개한다.
                 */
                firstJoinRelease.countDown();

                awaitLatch(
                        secondJoinEntered,
                        "두 번째 JOIN 진입"
                );

                awaitFuture(firstFuture);
                awaitFuture(secondFuture);

                // then - 모든 스레드 종료 후 최종 검증

                assertEquals(
                        1,
                        maxActiveJoinCount.get(),
                        "두 JOIN 작업의 임계 영역은 중첩되면 안 된다."
                );

                assertEquals(
                        0,
                        activeJoinCount.get()
                );

                assertEquals(
                        2,
                        subscribeCount.get()
                );

                assertEquals(
                        2,
                        subscriptions.size()
                );

                assertSame(
                        firstSubscription,
                        subscriptions.get(firstSubscription.id())
                );

                assertSame(
                        secondSubscription,
                        subscriptions.get(secondSubscription.id())
                );

                /*
                 * Mockito verify는 모든 작업이 종료된 후 수행한다.
                 */
                verify(session, times(1)).subscribe(
                        firstSubscription.id(),
                        firstSubscription.destination()
                );

                verify(session, times(1)).subscribe(
                        secondSubscription.id(),
                        secondSubscription.destination()
                );

                verifyJoinInvocation(
                        firstType,
                        firstSubscription
                );

                verifyJoinInvocation(
                        secondType,
                        secondSubscription
                );

                int expectedSendCount =
                        (firstType == SessionType.VIEWING ? 1 : 0)
                                + (secondType == SessionType.VIEWING ? 1 : 0);

                verify(viewingSessionService, times(expectedSendCount))
                        .send(eq(contentId), any());

            } finally {

                /*
                 * Assertion 실패 등의 상황에서도
                 * 작업 스레드가 영구 대기하지 않도록 한다.
                 */
                firstJoinRelease.countDown();
            }
        }

        /*
         * ==========================================
         * JOIN - LEAVE 직렬화 공통 검증 메서드
         * ==========================================
         */

        private void verifyJoinAndLeaveSerialization(
                SessionType type
        ) throws Exception {

            // given

            WSSubscription subscription = subscriptionStub(
                    "subscription-1",
                    type
            );

            if (type == SessionType.VIEWING) {

                sendViewingMessageStub(contentId);
            }

            CountDownLatch joinEntered = new CountDownLatch(1);
            CountDownLatch joinRelease = new CountDownLatch(1);

            CountDownLatch leaveEntered = new CountDownLatch(1);

            AtomicReference<Thread> joinThread =
                    new AtomicReference<>();

            AtomicReference<Thread> leaveThread =
                    new AtomicReference<>();

            AtomicInteger activeOperationCount = new AtomicInteger();
            AtomicInteger maxActiveOperationCount = new AtomicInteger();

            joinServiceStub(
                    type,
                    subscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "JOIN 작업은 Session Lock을 소유해야 한다."
                        );

                        int active =
                                activeOperationCount.incrementAndGet();

                        maxActiveOperationCount.accumulateAndGet(
                                active,
                                Math::max
                        );

                        try {

                            joinEntered.countDown();

                            awaitRelease(joinRelease);

                            return null;

                        } finally {
                            activeOperationCount.decrementAndGet();
                        }
                    }
            );

            leaveServiceStub(
                    type,
                    subscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "LEAVE 작업은 Session Lock을 소유해야 한다."
                        );

                        int active =
                                activeOperationCount.incrementAndGet();

                        maxActiveOperationCount.accumulateAndGet(
                                active,
                                Math::max
                        );

                        try {

                            leaveEntered.countDown();

                            return null;

                        } finally {
                            activeOperationCount.decrementAndGet();
                        }
                    }
            );

            try {

                // when

                Future<?> joinFuture = executorService.submit(() -> {

                    joinThread.set(Thread.currentThread());

                    executeJoin(type, subscription);
                });

                awaitLatch(
                        joinEntered,
                        "JOIN 작업 진입"
                );

                /*
                 * 세션 구독 등록은 완료되었지만
                 * 하위 서비스의 JOIN 작업은 진행 중이다.
                 */
                assertSame(
                        subscription,
                        subscriptions.get(subscription.id())
                );

                Future<?> leaveFuture = executorService.submit(() -> {

                    leaveThread.set(Thread.currentThread());

                    executeLeave(type, subscription);
                });

                awaitBlocked(
                        leaveThread,
                        joinThread,
                        session,
                        leaveFuture
                );

                // then - JOIN 임계 영역 종료 전

                assertEquals(
                        0,
                        getSubscriptionCount.get(),
                        "LEAVE의 구독 조회 작업은 대기해야 한다."
                );

                assertEquals(
                        0,
                        unsubscribeCount.get(),
                        "JOIN 진행 중에는 구독이 제거되면 안 된다."
                );

                assertEquals(
                        1L,
                        leaveEntered.getCount(),
                        "LEAVE 작업은 아직 진입하면 안 된다."
                );

                assertEquals(
                        1,
                        activeOperationCount.get()
                );

                assertFalse(joinFuture.isDone());

                assertFalse(leaveFuture.isDone());

                assertSame(
                        subscription,
                        subscriptions.get(subscription.id())
                );

                /*
                 * JOIN 작업을 재개한다.
                 */
                joinRelease.countDown();

                awaitLatch(
                        leaveEntered,
                        "LEAVE 작업 진입"
                );

                awaitFuture(joinFuture);
                awaitFuture(leaveFuture);

                // then - 모든 작업 종료 후

                assertEquals(
                        1,
                        maxActiveOperationCount.get(),
                        "JOIN과 LEAVE의 임계 영역은 중첩되면 안 된다."
                );

                assertEquals(
                        0,
                        activeOperationCount.get()
                );

                assertEquals(1, subscribeCount.get());

                assertEquals(1, getSubscriptionCount.get());

                assertEquals(1, unsubscribeCount.get());

                assertTrue(
                        subscriptions.isEmpty(),
                        "LEAVE 완료 후 세션 구독은 제거되어야 한다."
                );

                verify(session, times(1)).subscribe(
                        subscription.id(),
                        subscription.destination()
                );

                verify(session, times(1)).getSubscription(
                        subscription.id()
                );

                verify(session, times(1)).unsubscribe(
                        subscription
                );

                verifyJoinInvocation(type, subscription);

                verifyLeaveInvocation(type, subscription);

                verify(
                        viewingSessionService,
                        times(type == SessionType.VIEWING ? 2 : 0)
                ).send(eq(contentId), any());

            } finally {

                joinRelease.countDown();
            }
        }

        /*
         * ==========================================
         * Viewing 메시지 전송 동시성 공통 검증 메서드
         * ==========================================
         */

        private void verifyViewingMessageConcurrency(
                OperationType firstOperation,
                OperationType secondOperation
        ) throws Exception {

            // given

            sendViewingMessageStub(contentId);

            WSSubscription firstSubscription = subscriptionStub(
                    "subscription-1",
                    SessionType.VIEWING
            );

            /*
             * JOIN -> LEAVE인 경우 동일한 구독을 사용한다.
             * 나머지 경우는 서로 다른 구독을 사용한다.
             */
            WSSubscription secondSubscription =
                    firstOperation == OperationType.JOIN
                            && secondOperation == OperationType.LEAVE
                            ? firstSubscription
                            : subscriptionStub(
                            "subscription-2",
                            SessionType.VIEWING
                    );

            /*
             * 최초 작업이 LEAVE인 경우,
             * 기존에 구독이 등록되어 있다고 가정한다.
             */
            if (firstOperation == OperationType.LEAVE) {

                subscriptions.put(
                        firstSubscription.id(),
                        firstSubscription
                );
            }

            if (secondOperation == OperationType.LEAVE
                    && secondSubscription != firstSubscription) {

                subscriptions.put(
                        secondSubscription.id(),
                        secondSubscription
                );
            }

            CountDownLatch firstSendEntered = new CountDownLatch(1);
            CountDownLatch firstSendRelease = new CountDownLatch(1);

            CountDownLatch secondOperationEntered =
                    new CountDownLatch(1);

            CountDownLatch secondSendEntered = new CountDownLatch(1);
            CountDownLatch secondSendRelease = new CountDownLatch(1);

            AtomicInteger sendInvocationCount = new AtomicInteger();

            AtomicInteger activeSendCount = new AtomicInteger();
            AtomicInteger maxActiveSendCount = new AtomicInteger();

            /*
             * 첫 번째 구독 상태 변경 작업.
             */
            operationServiceStub(
                    firstOperation,
                    firstSubscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "첫 번째 구독 작업은 Session Lock 내부에서 실행되어야 한다."
                        );

                        return null;
                    }
            );

            /*
             * 두 번째 구독 상태 변경 작업.
             */
            operationServiceStub(
                    secondOperation,
                    secondSubscription,
                    invocation -> {

                        assertTrue(
                                Thread.holdsLock(session),
                                "두 번째 구독 작업은 Session Lock 내부에서 실행되어야 한다."
                        );

                        secondOperationEntered.countDown();

                        return null;
                    }
            );

            /*
             * send 작업은 Lock 외부에서 실행되어야 한다.
             */
            doAnswer(invocation -> {

                assertFalse(
                        Thread.holdsLock(session),
                        "메시지 전송 시 Session Lock을 소유하면 안 된다."
                );

                int invocationOrder =
                        sendInvocationCount.incrementAndGet();

                int active = activeSendCount.incrementAndGet();

                maxActiveSendCount.accumulateAndGet(
                        active,
                        Math::max
                );

                try {

                    if (invocationOrder == 1) {

                        firstSendEntered.countDown();

                        awaitRelease(firstSendRelease);

                    } else if (invocationOrder == 2) {

                        secondSendEntered.countDown();

                        awaitRelease(secondSendRelease);

                    } else {

                        fail("예상하지 않은 메시지 전송 호출");
                    }

                    return null;

                } finally {

                    activeSendCount.decrementAndGet();
                }

            }).when(viewingSessionService).send(
                    eq(contentId),
                    any()
            );

            try {

                // when - 첫 번째 Viewing 작업 수행

                Future<?> firstFuture = executorService.submit(() ->
                        executeViewingOperation(
                                firstOperation,
                                firstSubscription
                        )
                );

                awaitLatch(
                        firstSendEntered,
                        "첫 번째 메시지 전송 진입"
                );

                /*
                 * 첫 번째 구독 상태 변경은 완료됐지만
                 * 메시지 전송은 아직 진행 중이다.
                 */
                assertFalse(firstFuture.isDone());

                assertEquals(
                        1,
                        activeSendCount.get()
                );

                assertSubscriptionState(
                        firstSubscription,
                        firstOperation
                );

                /*
                 * 첫 번째 메시지 전송이 종료되지 않은 상태에서
                 * 두 번째 Viewing 작업을 실행한다.
                 */
                Future<?> secondFuture = executorService.submit(() ->
                        executeViewingOperation(
                                secondOperation,
                                secondSubscription
                        )
                );

                awaitLatch(
                        secondOperationEntered,
                        "두 번째 구독 상태 변경 작업 진입"
                );

                awaitLatch(
                        secondSendEntered,
                        "두 번째 메시지 전송 진입"
                );

                // then - 두 메시지 전송이 동시에 수행되는 상태

                assertFalse(
                        firstFuture.isDone(),
                        "첫 번째 메시지 전송은 아직 진행 중이어야 한다."
                );

                assertFalse(
                        secondFuture.isDone(),
                        "두 번째 메시지 전송도 진행 중이어야 한다."
                );

                assertEquals(
                        2,
                        activeSendCount.get(),
                        "두 메시지 전송 작업이 동시에 실행되어야 한다."
                );

                assertEquals(
                        2,
                        maxActiveSendCount.get(),
                        "메시지 전송의 실행 구간이 중첩되어야 한다."
                );

                assertSubscriptionState(
                        secondSubscription,
                        secondOperation
                );

                /*
                 * 두 번째 메시지 전송만 먼저 종료한다.
                 *
                 * 첫 번째 메시지 전송이 완료되지 않아도
                 * 두 번째 작업 전체가 완료될 수 있어야 한다.
                 */
                secondSendRelease.countDown();

                awaitFuture(secondFuture);

                assertFalse(
                        firstFuture.isDone(),
                        "두 번째 작업은 첫 번째 메시지 전송에 의존하면 안 된다."
                );

                assertEquals(
                        1,
                        activeSendCount.get()
                );

                /*
                 * 첫 번째 메시지 전송 재개.
                 */
                firstSendRelease.countDown();

                awaitFuture(firstFuture);

                // then - 전체 작업 종료 후 검증

                assertEquals(
                        0,
                        activeSendCount.get()
                );

                assertEquals(
                        2,
                        sendInvocationCount.get()
                );

                assertEquals(
                        2,
                        maxActiveSendCount.get()
                );

                verifyOperationInvocation(
                        firstOperation,
                        firstSubscription
                );

                verifyOperationInvocation(
                        secondOperation,
                        secondSubscription
                );

                verify(viewingSessionService, times(2)).send(
                        eq(contentId),
                        any()
                );

                if (firstSubscription == secondSubscription) {

                    /*
                     * 동일 구독에 대한 JOIN -> LEAVE 작업.
                     */
                    assertSubscriptionState(
                            secondSubscription,
                            secondOperation
                    );

                } else {

                    assertSubscriptionState(
                            firstSubscription,
                            firstOperation
                    );

                    assertSubscriptionState(
                            secondSubscription,
                            secondOperation
                    );
                }

            } finally {

                firstSendRelease.countDown();

                secondSendRelease.countDown();
            }
        }

        /*
         * ==========================================
         * 작업 실행 util method
         * ==========================================
         */

        private void executeJoin(
                SessionType type,
                WSSubscription subscription
        ) {

            switch (type) {

                case VIEWING ->
                        watchingSessionService.joinViewerSession(
                                subscription.sessionId(),
                                subscription.id(),
                                subscription.destination(),
                                auth
                        );

                case CHATTING ->
                        watchingSessionService.joinChattingSession(
                                subscription.sessionId(),
                                subscription.id(),
                                subscription.destination(),
                                auth
                        );
            }
        }

        private void executeLeave(
                SessionType type,
                WSSubscription subscription
        ) {

            switch (type) {

                case VIEWING ->
                        watchingSessionService.leaveViewerSession(
                                subscription.sessionId(),
                                subscription.id(),
                                auth
                        );

                case CHATTING ->
                        watchingSessionService.leaveChattingSession(
                                subscription.sessionId(),
                                subscription.id(),
                                auth
                        );
            }
        }

        private void executeViewingOperation(
                OperationType operation,
                WSSubscription subscription
        ) {

            switch (operation) {

                case JOIN ->
                        executeJoin(
                                SessionType.VIEWING,
                                subscription
                        );

                case LEAVE ->
                        executeLeave(
                                SessionType.VIEWING,
                                subscription
                        );
            }
        }

        /*
         * ==========================================
         * 동시성 util method
         * ==========================================
         */

        /**
         * 테스트 메인 스레드에서 작업 스레드의
         * 특정 지점 진입을 기다린다.
         *
         * 타임아웃은 테스트 메인 스레드에만 적용한다.
         */
        private void awaitLatch(
                CountDownLatch latch,
                String description
        ) throws InterruptedException {

            assertTrue(
                    latch.await(
                            TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    ),
                    description + " - 타임아웃 발생"
            );
        }

        /**
         * 작업 스레드의 실행을 중단한다.
         *
         * 작업 스레드가 자체 타임아웃으로
         * Session Lock을 해제하는 상황을 방지한다.
         *
         * 반드시 테스트의 finally에서 latch를 해제한다.
         */
        private void awaitRelease(
                CountDownLatch latch
        ) {

            try {

                latch.await();

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new AssertionError(
                        "작업 스레드 대기 중 인터럽트 발생",
                        e
                );
            }
        }

        /**
         * 작업 종료 및 작업 스레드 내부의 예외 검증.
         */
        private void awaitFuture(
                Future<?> future
        ) throws Exception {

            future.get(
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        }

        /**
         * 특정 스레드가 지정된 세션 객체의
         * 모니터 Lock을 대기하는지 검증한다.
         */
        private void awaitBlocked(
                AtomicReference<Thread> targetThread,
                AtomicReference<Thread> ownerThread,
                Object monitor,
                Future<?> targetFuture
        ) throws Exception {

            ThreadMXBean threadMXBean =
                    ManagementFactory.getThreadMXBean();

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

            while (System.nanoTime() < deadline) {

                /*
                 * Lock을 기다려야 하는 스레드가 먼저 종료되었다면
                 * 작업 내부 예외를 즉시 확인한다.
                 */
                if (targetFuture.isDone()) {

                    awaitFuture(targetFuture);

                    fail("Lock을 대기해야 하는 작업이 먼저 종료되었다.");
                }

                Thread target = targetThread.get();

                Thread owner = ownerThread.get();

                if (target != null && owner != null) {

                    ThreadInfo threadInfo =
                            threadMXBean.getThreadInfo(
                                    target.getId()
                            );

                    if (threadInfo != null
                            && threadInfo.getThreadState()
                            == Thread.State.BLOCKED
                            && threadInfo.getLockInfo() != null
                            && threadInfo.getLockOwnerId()
                            == owner.getId()
                            && threadInfo.getLockInfo()
                            .getIdentityHashCode()
                            == System.identityHashCode(monitor)) {

                        return;
                    }
                }

                LockSupport.parkNanos(
                        TimeUnit.MILLISECONDS.toNanos(1)
                );
            }

            Thread target = targetThread.get();

            fail(
                    "Session Lock 대기가 확인되지 않았다."
                            + " 현재 스레드 상태: "
                            + (target == null
                            ? "NOT_STARTED"
                            : target.getState())
            );
        }

        /*
         * ==========================================
         * 검증 util method
         * ==========================================
         */

        private void assertSubscriptionState(
                WSSubscription subscription,
                OperationType operation
        ) {

            switch (operation) {

                case JOIN ->
                        assertSame(
                                subscription,
                                subscriptions.get(subscription.id()),
                                "JOIN 완료 후 구독이 등록되어야 한다."
                        );

                case LEAVE ->
                        assertFalse(
                                subscriptions.containsKey(subscription.id()),
                                "LEAVE 완료 후 구독이 제거되어야 한다."
                        );
            }
        }

        private void verifyJoinInvocation(
                SessionType type,
                WSSubscription subscription
        ) {

            switch (type) {

                case VIEWING ->
                        verify(viewingSessionService, times(1))
                                .join(subscription, contentId);

                case CHATTING ->
                        verify(chattingSessionService, times(1))
                                .join(subscription, contentId);
            }
        }

        private void verifyLeaveInvocation(
                SessionType type,
                WSSubscription subscription
        ) {

            switch (type) {

                case VIEWING ->
                        verify(viewingSessionService, times(1))
                                .leave(subscription, contentId);

                case CHATTING ->
                        verify(chattingSessionService, times(1))
                                .leave(subscription, contentId);
            }
        }

        private void verifyOperationInvocation(
                OperationType operation,
                WSSubscription subscription
        ) {

            switch (operation) {

                case JOIN -> {

                    verify(session, times(1)).subscribe(
                            subscription.id(),
                            subscription.destination()
                    );

                    verify(viewingSessionService, times(1))
                            .join(subscription, contentId);
                }

                case LEAVE -> {

                    verify(session, times(1)).getSubscription(
                            subscription.id()
                    );

                    verify(viewingSessionService, times(1))
                            .leave(subscription, contentId);

                    verify(session, times(1)).unsubscribe(
                            subscription
                    );
                }
            }
        }

        /*
         * ==========================================
         * Session stubs
         * ==========================================
         */

        private WSSession sessionStub() {

            WSSession mockSession = mock(WSSession.class);

            /*
             * 세션 구독 등록.
             */
            lenient().doAnswer(invocation -> {

                subscribeCount.incrementAndGet();

                assertTrue(
                        Thread.holdsLock(mockSession),
                        "subscribe 작업은 Session Lock 내부에서 실행되어야 한다."
                );

                String subscriptionId = invocation.getArgument(0);

                String destination = invocation.getArgument(1);

                WSSubscription subscription =
                        preparedSubscriptions.get(subscriptionId);

                assertNotNull(
                        subscription,
                        "사전에 준비되지 않은 구독이다."
                );

                assertEquals(
                        subscription.destination(),
                        destination
                );

                WSSubscription previous =
                        subscriptions.putIfAbsent(
                                subscriptionId,
                                subscription
                        );

                assertNull(
                        previous,
                        "이미 등록된 구독이다."
                );

                return subscription;

            }).when(mockSession).subscribe(
                    anyString(),
                    anyString()
            );

            /*
             * 세션 구독 조회.
             */
            lenient().doAnswer(invocation -> {

                getSubscriptionCount.incrementAndGet();

                assertTrue(
                        Thread.holdsLock(mockSession),
                        "getSubscription 작업은 Session Lock 내부에서 실행되어야 한다."
                );

                String subscriptionId = invocation.getArgument(0);

                return Optional.ofNullable(
                        subscriptions.get(subscriptionId)
                );

            }).when(mockSession).getSubscription(anyString());

            /*
             * 세션 구독 제거.
             */
            lenient().doAnswer(invocation -> {

                unsubscribeCount.incrementAndGet();

                assertTrue(
                        Thread.holdsLock(mockSession),
                        "unsubscribe 작업은 Session Lock 내부에서 실행되어야 한다."
                );

                WSSubscription subscription = invocation.getArgument(0);

                boolean removed = subscriptions.remove(
                        subscription.id(),
                        subscription
                );

                assertTrue(
                        removed,
                        "등록된 구독이 정상적으로 제거되어야 한다."
                );

                return null;

            }).when(mockSession).unsubscribe(
                    any(WSSubscription.class)
            );

            return mockSession;
        }

        private WSSubscription subscriptionStub(
                String subscriptionId,
                SessionType type
        ) {

            WSSubscription subscription = new WSSubscription(
                    sessionId,
                    subscriptionId,
                    destination(contentId, type)
            );

            preparedSubscriptions.put(
                    subscriptionId,
                    subscription
            );

            return subscription;
        }

        /*
         * ==========================================
         * 비즈니스 서비스 stubs
         * ==========================================
         */

        private void joinServiceStub(
                SessionType type,
                WSSubscription subscription,
                Answer<Void> answer
        ) {

            switch (type) {

                case VIEWING ->
                        doAnswer(answer)
                                .when(viewingSessionService)
                                .join(subscription, contentId);

                case CHATTING ->
                        doAnswer(answer)
                                .when(chattingSessionService)
                                .join(subscription, contentId);
            }
        }

        private void leaveServiceStub(
                SessionType type,
                WSSubscription subscription,
                Answer<Void> answer
        ) {

            switch (type) {

                case VIEWING ->
                        doAnswer(answer)
                                .when(viewingSessionService)
                                .leave(subscription, contentId);

                case CHATTING ->
                        doAnswer(answer)
                                .when(chattingSessionService)
                                .leave(subscription, contentId);
            }
        }

        private void operationServiceStub(
                OperationType operation,
                WSSubscription subscription,
                Answer<Void> answer
        ) {

            switch (operation) {

                case JOIN ->
                        joinServiceStub(
                                SessionType.VIEWING,
                                subscription,
                                answer
                        );

                case LEAVE ->
                        leaveServiceStub(
                                SessionType.VIEWING,
                                subscription,
                                answer
                        );
            }
        }
    }

    /*
     * ==========================================
     * 공통 util method
     * ==========================================
     */

    String destination(
            UUID contentId,
            String postFix
    ) {

        return PATH_PREFIX + contentId + postFix;
    }

    String destination(
            UUID contentId,
            SessionType type
    ) {

        return switch (type) {

            case VIEWING ->
                    destination(contentId, VIEW_POSTFIX);

            case CHATTING ->
                    destination(contentId, CHAT_POSTFIX);
        };
    }

    /*
     * ==========================================
     * 공통 stubs
     * ==========================================
     */

    void sessionRepositoryStub(
            String sessionId,
            WSSession session
    ) {

        when(sessionRegistry.query(sessionId))
                .thenReturn(Optional.of(session));
    }

    /**
     * Viewing 메시지 조립 및 이벤트 발행에 필요한 Stub.
     */
    void sendViewingMessageStub(UUID contentId) {

        UUID userId = UUID.randomUUID();

        ContentDto content = mock(ContentDto.class);

        User user = mock(User.class);

        Watcher watcher = mock(Watcher.class);

        WatchContent watchContent = mock(WatchContent.class);

        // 인증 정보
        when(auth.getUserId()).thenReturn(userId);

        // 콘텐츠 정보
        when(contentService.getById(contentId))
                .thenReturn(content);

        when(content.id()).thenReturn(contentId);

        // 사용자 정보
        when(userService.getById(userId))
                .thenReturn(user);

        // 메시지 DTO 조립
        when(mapper.toWatcher(user))
                .thenReturn(watcher);

        when(mapper.toWatchContent(content))
                .thenReturn(watchContent);

        /*
         * JOIN 이벤트 발행에 사용되는 정보.
         *
         * LEAVE만 수행하는 테스트에서는 호출되지 않을 수 있기
         * 때문에 lenient를 적용한다.
         */
        lenient().when(user.getId())
                .thenReturn(userId);

        lenient().when(user.getName())
                .thenReturn("test-user");

        lenient().when(content.title())
                .thenReturn("test-content");
    }
}