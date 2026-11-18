package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription;

import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketSubscriptionRegistryTest {

    private WebSocketSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSubscriptionRegistryImpl();
    }

    private record AddResult(
            WSSubscription subscription,
            boolean success
    ) {
    }

    private AddResult tryAdd(WSSubscription subscription) {
        try {
            WSSubscription result = registry.add(subscription);

            assertThat(result).isSameAs(subscription);

            return new AddResult(subscription, true);

        } catch (IllegalStateException e) {
            return new AddResult(subscription, false);
        }
    }

    private WSSubscription createSubscription(String id) {
        WSSubscription subscription = mock(WSSubscription.class);

        when(subscription.id()).thenReturn(id);
        when(subscription.uuid()).thenReturn(UUID.randomUUID());

        return subscription;
    }

    // 공통 검증 메서드

    private void assertRegistered(WSSubscription subscription) {
        assertThat(registry.get(subscription.id()))
                .containsSame(subscription);

        assertThat(registry.get(subscription.uuid()))
                .containsSame(subscription);
    }

    private void assertRemoved(WSSubscription subscription) {
        assertThat(registry.get(subscription.id())).isEmpty();
        assertThat(registry.get(subscription.uuid())).isEmpty();
    }


    // 동시성 테스트용 매서드
    private <T> List<T> executeConcurrently(
            Callable<T> firstTask,
            Callable<T> secondTask
    ) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Future<T> first = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                return firstTask.call();
            });

            Future<T> second = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();

                return secondTask.call();
            });

            assertThat(readyLatch.await(5, TimeUnit.SECONDS))
                    .isTrue();

            startLatch.countDown();

            return Arrays.asList(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );

        } finally {
            executor.shutdownNow();
        }
    }

    @Nested
    @DisplayName("구독 등록")
    class AddTest {

        @Test
        @DisplayName("구독을 등록하면 ID와 UUID로 조회할 수 있다")
        void addSuccess() {
            // given
            WSSubscription subscription = createSubscription("sub-1");

            // when
            WSSubscription result = registry.add(subscription);

            // then
            assertThat(result).isSameAs(subscription);

            assertRegistered(subscription);
        }

        @Test
        @DisplayName("null 구독은 등록할 수 없다")
        void addNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> registry.add(null));

            assertThat(registry.getAll()).isEmpty();
        }

        @Test
        @DisplayName("동일한 ID의 구독을 중복 등록할 수 없다")
        void addDuplicate() {
            // given
            WSSubscription original = createSubscription("sub-1");
            WSSubscription duplicate = createSubscription("sub-1");

            registry.add(original);

            // when
            assertThatThrownBy(() -> registry.add(duplicate))
                    .isInstanceOf(IllegalStateException.class);

            // then
            assertRegistered(original);

            assertThat(registry.get(duplicate.uuid())).isEmpty();

            assertThat(registry.getAll())
                    .containsExactly(original);
        }

        @Test
        @DisplayName("서로 다른 ID의 구독은 모두 등록된다")
        void addDifferentSubscriptions() {
            // given
            WSSubscription first = createSubscription("sub-1");
            WSSubscription second = createSubscription("sub-2");

            // when
            registry.add(first);
            registry.add(second);

            // then
            assertRegistered(first);
            assertRegistered(second);

            assertThat(registry.getAll())
                    .containsExactlyInAnyOrder(first, second);
        }
    }

    @Nested
    @DisplayName("구독 조회")
    class GetTest {

        @Test
        @DisplayName("ID로 구독을 조회할 수 있다")
        void getById() {
            WSSubscription subscription = createSubscription("sub-1");
            registry.add(subscription);

            assertThat(registry.get(subscription.id()))
                    .containsSame(subscription);
        }

        @Test
        @DisplayName("UUID로 구독을 조회할 수 있다")
        void getByUUID() {
            WSSubscription subscription = createSubscription("sub-1");
            registry.add(subscription);

            assertThat(registry.get(subscription.uuid()))
                    .containsSame(subscription);
        }

        @Test
        @DisplayName("존재하지 않는 ID는 빈 Optional을 반환한다")
        void getUnknownId() {
            assertThat(registry.get("unknown")).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 UUID는 빈 Optional을 반환한다")
        void getUnknownUUID() {
            assertThat(registry.get(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("등록된 모든 구독을 조회할 수 있다")
        void getAll() {
            WSSubscription first = createSubscription("sub-1");
            WSSubscription second = createSubscription("sub-2");

            registry.add(first);
            registry.add(second);

            assertThat(registry.getAll())
                    .containsExactlyInAnyOrder(first, second);
        }

        @Test
        @DisplayName("조회한 리스트를 수정해도 Registry에 영향이 없다")
        void getAllReturnsIndependentList() {
            WSSubscription subscription = createSubscription("sub-1");
            registry.add(subscription);

            List<WSSubscription> result = registry.getAll();

            result.clear();

            assertThat(registry.getAll())
                    .containsExactly(subscription);
        }
    }

    @Nested
    @DisplayName("구독 삭제")
    class RemoveTest {

        @Test
        @DisplayName("등록된 객체를 삭제하면 구독과 인덱스가 제거된다")
        void removeSuccess() {
            // given
            WSSubscription subscription = createSubscription("sub-1");
            registry.add(subscription);

            // when
            registry.remove(subscription);

            // then
            assertRemoved(subscription);
            assertThat(registry.getAll()).isEmpty();
        }

        @Test
        @DisplayName("ID가 같아도 다른 객체라면 삭제되지 않는다")
        void removeDifferentInstance() {
            // given
            WSSubscription original = createSubscription("sub-1");
            WSSubscription other = createSubscription("sub-1");

            registry.add(original);

            // when
            registry.remove(other);

            // then
            assertRegistered(original);

            assertThat(registry.getAll())
                    .containsExactly(original);
        }

        @Test
        @DisplayName("존재하지 않는 구독을 삭제해도 상태가 유지된다")
        void removeUnknownSubscription() {
            WSSubscription original = createSubscription("sub-1");
            WSSubscription unknown = createSubscription("sub-2");

            registry.add(original);

            assertThatCode(() -> registry.remove(unknown))
                    .doesNotThrowAnyException();

            assertRegistered(original);

            assertThat(registry.getAll())
                    .containsExactly(original);
        }

        @Test
        @DisplayName("이전에 삭제된 객체로 새로 등록된 객체를 삭제할 수 없다")
        void removeStaleSubscription() {
            // given
            WSSubscription old = createSubscription("sub-1");
            WSSubscription current = createSubscription("sub-1");

            registry.add(old);
            registry.remove(old);
            registry.add(current);

            // when
            registry.remove(old);

            // then
            assertRegistered(current);
            assertThat(registry.get(old.uuid())).isEmpty();
        }
    }

    @Nested
    @DisplayName("전체 삭제")
    class ClearTest {

        @Test
        @DisplayName("모든 구독과 인덱스를 제거한다")
        void clearAll() {
            WSSubscription first = createSubscription("sub-1");
            WSSubscription second = createSubscription("sub-2");

            registry.add(first);
            registry.add(second);

            registry.clear();

            assertRemoved(first);
            assertRemoved(second);

            assertThat(registry.getAll()).isEmpty();
        }
    }




    @Nested
    @DisplayName("구독 Registry 동시성 테스트")
    class ConcurrencyTest {

        @RepeatedTest(50)
        @DisplayName("동일한 ID로 동시에 등록하면 하나의 구독만 등록된다")
        void concurrentAddSameId() throws Exception {
            // given
            WSSubscription first = createSubscription("sub-1");
            WSSubscription second = createSubscription("sub-1");

            // when
            List<AddResult> results = executeConcurrently(
                    () -> tryAdd(first),
                    () -> tryAdd(second)
            );

            // then
            List<AddResult> successes = results.stream()
                    .filter(AddResult::success)
                    .toList();

            List<AddResult> failures = results.stream()
                    .filter(result -> !result.success())
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);

            WSSubscription winner = successes.get(0).subscription();
            WSSubscription loser = failures.get(0).subscription();

            // 성공한 구독만 Registry에 존재한다.
            assertThat(registry.getAll())
                    .containsExactly(winner);

            // ID와 UUID 인덱스가 일치한다.
            assertRegistered(winner);

            // 실패한 객체의 UUID 인덱스는 존재하지 않는다.
            assertThat(registry.get(loser.uuid())).isEmpty();
        }

        @RepeatedTest(50)
        @DisplayName("서로 다른 ID로 동시에 등록하면 모두 저장된다")
        void concurrentAddDifferentIds() throws Exception {
            // given
            WSSubscription first = createSubscription("sub-1");
            WSSubscription second = createSubscription("sub-2");

            // when
            executeConcurrently(
                    () -> registry.add(first),
                    () -> registry.add(second)
            );

            // then
            assertThat(registry.getAll())
                    .containsExactlyInAnyOrder(first, second);

            assertRegistered(first);
            assertRegistered(second);
        }

        @RepeatedTest(50)
        @DisplayName("동일한 구독을 동시에 삭제해도 정상적으로 처리된다")
        void concurrentRemoveSameSubscription() throws Exception {
            // given
            WSSubscription subscription = createSubscription("sub-1");

            registry.add(subscription);

            // when
            executeConcurrently(
                    () -> {
                        registry.remove(subscription);
                        return null;
                    },
                    () -> {
                        registry.remove(subscription);
                        return null;
                    }
            );

            // then
            assertRemoved(subscription);
            assertThat(registry.getAll()).isEmpty();
        }

        @RepeatedTest(50)
        @DisplayName("동일한 ID의 등록과 삭제가 경합해도 정합성이 유지된다")
        void concurrentAddAndRemove() throws Exception {
            // given
            WSSubscription original = createSubscription("sub-1");
            WSSubscription replacement = createSubscription("sub-1");

            registry.add(original);

            // when
            List<Boolean> results = executeConcurrently(
                    () -> {
                        registry.remove(original);
                        return true;
                    },
                    () -> tryAdd(replacement).success()
            );

            // then
            boolean addSuccess = results.get(1);

            assertThat(registry.get(original.uuid())).isEmpty();

            if (addSuccess) {
                assertRegistered(replacement);

                assertThat(registry.getAll())
                        .containsExactly(replacement);
            } else {
                assertRemoved(replacement);
                assertThat(registry.getAll()).isEmpty();
            }
        }
    }

    @Test
    @DisplayName("구독 삭제 후 동일 ID로 재등록해도 이전 UUID로 조회되지 않는다")
    void indexConsistencyAfterReregister() {
        // given
        WSSubscription original = createSubscription("sub-1");
        WSSubscription replacement = createSubscription("sub-1");

        registry.add(original);

        // when
        registry.remove(original);
        registry.add(replacement);

        // then
        assertThat(registry.get(original.uuid())).isEmpty();

        assertRegistered(replacement);

        assertThat(registry.getAll())
                .containsExactly(replacement);
    }


    @Test
    @DisplayName("전체 삭제 후 재등록해도 이전 UUID 인덱스가 남지 않는다")
    void indexConsistencyAfterClear() {
        // given
        WSSubscription original = createSubscription("sub-1");
        WSSubscription replacement = createSubscription("sub-1");

        registry.add(original);

        // when
        registry.clear();
        registry.add(replacement);

        // then
        assertThat(registry.get(original.uuid())).isEmpty();
        assertRegistered(replacement);
    }
}

