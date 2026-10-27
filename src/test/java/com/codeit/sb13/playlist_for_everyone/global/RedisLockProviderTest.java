package com.codeit.sb13.playlist_for_everyone.global;

import com.codeit.sb13.playlist_for_everyone.global.service.RedisLockProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@DisplayName("Redis 분산락 획득을 위한 테스트")
@Disabled("자동 테스트에서는 서버 세팅때문에 제외")
public class RedisLockProviderTest {

    private String REDIS_URL = "localhost";
    private Integer REDIS_PORT = 6379;
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;

    private RedisLockProvider provider;

    @BeforeEach
    void setup(){
        factory = new LettuceConnectionFactory(REDIS_URL, REDIS_PORT);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        provider = new RedisLockProvider(template);
    }

    @AfterEach
    void teardown(){
        clear();
        factory.destroy();
    }

    private void clear(){
        Set<String> keys = template.keys("lock:*");
        for(String key : keys)
            template.delete(key);
    }

    private String getLockId(String key){
        return template.opsForValue().get("lock:" + key);
    }

    @Test
    @DisplayName("Redis 락 획득 후 언락 테스트")
    void lockTest(){
        String value = provider.lock("test");

        log.info("[RedisLockTest] test 락 획득 - id: {}",value);

        // 저장이 되었는지 확인
        String savedValue = getLockId("test");

        log.info("[RedisLockTest] 저장된 value 확인 - value: {}",savedValue);

        assertThat(savedValue).isEqualTo(value);

        provider.unlock("test",value);

        String unlockedValue = getLockId("test");
        assertThat(unlockedValue).isNullOrEmpty();


    }

    @Test
    @DisplayName("Redis 락 획득 후 같은 키 값으로 락 획득 불가")
    void duplicationLockTest(){
        String first = provider.lock("test");
        log.info("[RedisLockTest] test 락 획득 - id: {}",first);

        assertThatThrownBy(() -> provider.lock("test"))
                .isInstanceOf(RedisLockProvider.RedisLockException.class);
    }

    @Test
    @DisplayName("Redis 락 획득 후 다른 id 값으로 으로 락 해제 불가")
    void differentIdUnlockTest(){
        String value = provider.lock("test");

        log.info("[RedisLockTest] test 락 획득 - id: {}",value);

        // 저장이 되었는지 확인
        String savedValue = getLockId("test");

        log.info("[RedisLockTest] 저장된 value 확인 - value: {}",savedValue);

        provider.unlock("test","SDFSDFSFDF");

        String unlockedValue = getLockId("test");
        assertThat(unlockedValue).isEqualTo(savedValue);
    }

    @Test
    @DisplayName("동시성 시나리오 테스트")
    void concurrentTest(){
        int threads = 10;
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        AtomicInteger unshareResourceAccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);



        try{
            for(int i = 0; i < threads; i++){
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();  // 아래의 start.countDown 까지 대기
                        try {
                            provider.lock("test");
                            unshareResourceAccessCount.incrementAndGet();
                            Thread.sleep(100);
                        } catch (RedisLockProvider.RedisLockException e) {
                            throw e;
                        }
                        success.incrementAndGet();
                    } catch (RedisLockProvider.RedisLockException e) {
                        fail.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await(5, TimeUnit.SECONDS);

            start.countDown();  // executor 시작점

            done.await(10, TimeUnit.SECONDS);

            assertThat(success.get()).isEqualTo(1);
            assertThat(fail.get()).isEqualTo(9);
            assertThat(unshareResourceAccessCount.get()).isEqualTo(1);

        } catch (InterruptedException e) {
            log.error("[RedisLockTest] 동시성 테스트 에러",e);
        } finally {
            start.countDown();
            executor.shutdown();
        }

    }

    // todo - TTL 시간 만료 타이밍의 경쟁 상태에서 동시 진입시,
    //  락을 얻는 스레드가 락을 지우는 스레드보다 빨라서 락이 사라지는 경우 테스트

}
