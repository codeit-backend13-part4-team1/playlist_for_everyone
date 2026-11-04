package com.codeit.sb13.playlist_for_everyone.global.websocket.registry.redis;


import com.codeit.sb13.playlist_for_everyone.global.service.lock.SpreadLock;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("컨텐츠 별 채팅 등록 레지스트리 테스트")
public class RedisTopicRegistryTest {

    @Mock
    RedisBrokerRegister redisBroker;
    @Mock
    RedisTemplate<String,Object> redisTemplate;
    @Mock
    GenericJacksonJsonRedisSerializer serializer;
    @Spy
    SpreadLock locks;
    @InjectMocks
    RedisTopicRegistry registry;

    TestMessageListener listener = new TestMessageListener();

    class TestMessageListener extends RedisMessageListener{

        public TestMessageListener(){
            super(redisTemplate,serializer);
        }

        @Override
        public void run(Object o){
            // test logic
        }
    }

    @RequiredArgsConstructor
    @Getter
    class InvParams{
        private final RedisMessageListener listener;
        private final String topic;
    }

    private List<InvParams> getRes(String method){
        return mockingDetails(redisBroker)
                    .getInvocations()
                    .stream()
                    .filter(inv -> inv.getMethod().getName().equals(method))
                    .map(inv -> new InvParams((RedisMessageListener) inv.getArguments()[0], (String) inv.getArguments()[1]))
                    .toList();
    }


    @Nested
    @DisplayName("구독 테스트")
    class Register{
        @Test
        @DisplayName("특정 세션이 컨텐츠를 구독하면, 메세지 리스너가 redis 메세지를 구독함.")
        void registerTest(){
            String contentId = UUID.randomUUID().toString();
            String sessionId = "test-session";
            String subscribeId = "subscribeId";

            registry.register(contentId, sessionId, subscribeId, listener);

            verify(redisBroker, times(1)).register(eq(listener),eq(contentId));

            List<InvParams> res = getRes("register");

            assertThat(res).hasSize(1);
            assertThat(res.get(0).getListener()).isEqualTo(listener);
            assertThat(res.get(0).getTopic()).isEqualTo(contentId);
        }

        @Test
        @DisplayName("특정 세션이 컨텐츠를 중복 구독하면, 한 번만 redis 메세지를 구독함")
        void registerDuplicateTest(){
            String contentId = UUID.randomUUID().toString();
            String sessionId = "test-session";
            String subscribeId = "subscribeId";

            registry.register(contentId, sessionId, subscribeId, listener);
            registry.register(contentId, sessionId, subscribeId, listener);

            verify(redisBroker, times(1)).register(eq(listener),eq(contentId));
        }
    }


    @Nested
    @DisplayName("구독 취소 테스트")
    class Unregister{
        String contentId = UUID.randomUUID().toString();
        String sessionId = "test-session";
        String subscribeId = "subscribeId";

        @BeforeEach
        void set(){
            registry.register(contentId, sessionId, subscribeId, listener);
        }

        @AfterEach
        void reset(){
            registry.disconnect(sessionId);
        }

        @Test
        @DisplayName("특정 세션이 컨텐츠를 구독을 취소했을 때, 구독된 세션이 없으면 메세지 구독을 취소함")
        void unregisterTest(){
            registry.unregister(sessionId, subscribeId);

            verify(redisBroker, times(1)).unregister(eq(listener),eq(contentId));

            List<InvParams> res = getRes("unregister");

            assertThat(res).hasSize(1);
            assertThat(res.get(0).getListener()).isEqualTo(listener);
            assertThat(res.get(0).getTopic()).isEqualTo(contentId);
        }

        @Test
        @DisplayName("컨텐츠에 구독 세션이 남아있다면, 메세지 구독 유지")
        void unregisterTest2(){
            String sessionId = "test2-session";
            String subscribeId = "subscribeId2";

            registry.register(contentId, sessionId, subscribeId, listener);

            registry.unregister(sessionId, subscribeId);

            verify(redisBroker, times(0)).unregister(eq(listener),eq(contentId));
        }

    }

    @Nested
    @DisplayName("세션 삭제 테스트")
    class SessionDelete {
        @Test
        @DisplayName("세션 삭제시 세션에 연관된 모든 연결을 취소함")
        void unregisterTest() {
            String contentId = "test-content-Id";
            String sessionId = "test-session";
            String subscribeId = "subscribeId";
            String subscribeId2 = "subscribeId2";
            String subscribeId3 = "subscribeId3";


            registry.register(contentId, sessionId, subscribeId, listener);
            registry.register(contentId, sessionId, subscribeId2, listener);
            registry.register(contentId, sessionId, subscribeId3, listener);

            registry.disconnect(sessionId);

            verify(redisBroker, times(1)).register(eq(listener), eq(contentId));
            verify(redisBroker, times(1)).unregister(eq(listener), eq(contentId));

            List<InvParams> res = getRes("register");

            assertThat(res).hasSize(1);
            assertThat(res.get(0).getListener()).isEqualTo(listener);
            assertThat(res.get(0).getTopic()).isEqualTo(contentId);

            List<InvParams> res2 = getRes("unregister");

            assertThat(res2).hasSize(1);
            assertThat(res2.get(0).getListener()).isEqualTo(listener);
            assertThat(res2.get(0).getTopic()).isEqualTo(contentId);
        }
    }


    @Nested
    @DisplayName("경합성 테스트")
    class Concurrency{
        String contentId = "test-content-Id";
        String sessionId = "test-session";
        String subscribeId = "subscribeId";

        void register(){
            registry.register(contentId,sessionId,subscribeId,listener);
        }

        void unregister(){
            registry.unregister(sessionId,subscribeId);
        }

        void threadRunner (
                int thread,
                int successCounter,
                int failCounter,
                Consumer<?> fnc
        ) throws Exception {

            ExecutorService executor = Executors.newFixedThreadPool(thread);

            CountDownLatch ready = new CountDownLatch(thread);
            CountDownLatch start = new CountDownLatch(1);

            ArrayList<Future<?>> futures = new ArrayList<>();

            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger fail = new AtomicInteger(0);

            try{
                for (int i=0; i<thread; i++){
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            // 함수 실행
                            fnc.accept(null);
                            success.incrementAndGet();
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }

                        return null;
                    }));
                    log.info("[ContentChatTest] Future 세팅 완료 - {}",i);
                }

                assertTrue(
                        ready.await(5, TimeUnit.SECONDS),
                        "스레드 준비 시간 초과"
                );

                start.countDown();

                for (Future<?> fn : futures){
                    fn.get(10, TimeUnit.SECONDS);
                    log.info("[ContentChatTest] Future 실행 완료 - {}",fn);
                }

            } finally {
                start.countDown();
                executor.shutdownNow();
            }

            // 함수 성공, 에러 검정
            assertEquals(successCounter, success.get());
            assertEquals(failCounter, fail.get());
        }

        @Test
        @DisplayName("등록요청이 동시에 들어와도 메세지 구독은 단일하게 유지됨")
        void concurrencyRegisterTest() throws Exception {
            threadRunner(3,3,0, k -> register());
            verify(redisBroker, times(1)).register(eq(listener),anyString());

        }

        @Test
        @DisplayName("해제요청이 동시에 들어와도, 메세지 해제는 단일하게 일어남")
        void concurrencyUnregisterTest() throws Exception {
            // 사전 등록
            register();

            threadRunner(3,3,0, k -> unregister());
            verify(redisBroker, times(1)).unregister(eq(listener),anyString());
        }
    }


}
