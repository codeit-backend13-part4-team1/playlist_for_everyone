package com.codeit.sb13.playlist_for_everyone.domain.websocket.chat;


import com.codeit.sb13.playlist_for_everyone.global.service.RedisBrokerRegister;
import com.codeit.sb13.playlist_for_everyone.global.websocket.chat.ChatMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.chat.ContentChatRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.CHAT_CONTENT_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("컨텐츠 별 채팅 등록 레지스트리 테스트")
public class ContentChatRegistryTest {

    @Mock
    RedisBrokerRegister redisBroker;

    @Mock
    ChatMessageListener listener;

    @InjectMocks
    ContentChatRegistry registry;

    private String topic(String contentId){
        return CHAT_CONTENT_PREFIX + contentId;
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

            registry.register(contentId, sessionId, subscribeId);

            verify(redisBroker, times(1)).register(eq(listener),eq(topic(contentId)));
        }

        @Test
        @DisplayName("특정 세션이 컨텐츠를 중복 구독하면, 한 번만 redis 메세지를 구독함")
        void registerDuplicateTest(){
            String contentId = UUID.randomUUID().toString();
            String sessionId = "test-session";
            String subscribeId = "subscribeId";

            registry.register(contentId, sessionId, subscribeId);
            registry.register(contentId, sessionId, subscribeId);

            verify(redisBroker, times(1)).register(eq(listener),eq(topic(contentId)));
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
            registry.register(contentId, sessionId, subscribeId);
        }

        @AfterEach
        void reset(){
            registry.disconnect(sessionId);
        }

        @Test
        @DisplayName("특정 세션이 컨텐츠를 구독을 취소했을 때, 구독된 세션이 없으면 메세지 구독을 취소함")
        void unregisterTest(){
            registry.unregister(sessionId, subscribeId);

            verify(redisBroker, times(1)).unregister(eq(listener),eq(topic(contentId)));
        }

        @Test
        @DisplayName("컨텐츠에 구독 세션이 남아있다면, 메세지 구독 유지")
        void unregisterTest2(){
            String sessionId = "test2-session";
            String subscribeId = "subscribeId2";

            registry.register(contentId, sessionId, subscribeId);

            registry.unregister(sessionId, subscribeId);

            verify(redisBroker, times(0)).unregister(eq(listener),eq(topic(contentId)));
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


            registry.register(contentId, sessionId, subscribeId);
            registry.register(contentId, sessionId, subscribeId2);
            registry.register(contentId, sessionId, subscribeId3);

            registry.disconnect(sessionId);

            verify(redisBroker, times(1)).register(eq(listener), eq(topic(contentId)));
            verify(redisBroker, times(1)).unregister(eq(listener), eq(topic(contentId)));
        }
    }


    @Nested
    @DisplayName("경합성 테스트")
    class Concurrency{
        String contentId = "test-content-Id";
        String sessionId = "test-session";
        String subscribeId = "subscribeId";

        void register(){
            registry.register(contentId,sessionId,subscribeId);
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
