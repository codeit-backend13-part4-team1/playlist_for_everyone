package com.codeit.sb13.playlist_for_everyone.watch;

import com.codeit.sb13.playlist_for_everyone.global.service.WatcherRegistry;
import com.codeit.sb13.playlist_for_everyone.global.service.RedisLockProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;
import static com.codeit.sb13.playlist_for_everyone.global.config.RedisConfig.*;


@Slf4j
@DisplayName("동시 시청 세션 저장소 테스트")
@Import({})
public class WatcherRegistryTest {

    private String REDIS_URL = "localhost";
    private Integer REDIS_PORT = 6379;
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;

    @Mock
    private RedisLockProvider lockProvider = mock(RedisLockProvider.class);

    private WatcherRegistry watcherRegistry;

    @BeforeEach
    void setup(){
        factory = new LettuceConnectionFactory(REDIS_URL, REDIS_PORT);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        watcherRegistry = new WatcherRegistry(template, lockProvider);
    }

    @AfterEach
    void teardown(){
        clear();
        factory.destroy();
    }

    private void mockupLockSet(){
        String lockId = UUID.randomUUID().toString();

        given(lockProvider.lock(anyString())).willReturn(lockId);

        willDoNothing().given(lockProvider).unlock(anyString(),eq(lockId));
    }

    private void clear(){
        Set<String> keys = template.keys("watcher:*");
        for(String key : keys)
            template.delete(key);
    }

    private boolean checkContainedChannelIdInList(String sessionId, String targetId){
        Set<String> idList = template.opsForSet().members(WATCHER_SESSION_PREFIX + sessionId);
        for(String id : idList){
            log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
            if (targetId.equals(id)) return true;
        }
        return false;
    }
    private boolean checkContainedSessionIdInList(String contentId, String targetId){
        Set<String> idList = template.opsForSet().members(WATCHER_CONTENT_PREFIX + contentId);
        for (String id : idList) {
            log.info("[WatcherRegistryTest] id 검색됨 - {}",id);
            if (targetId.equals(id)) return true;
        }
        return false;
    }


    @Test
    @DisplayName("컨텐츠와 현재세션번호를 기반으로 시청 등록 후 세션 id 기반 삭제 테스트")
    void registerWatcher(){
        mockupLockSet();

        String contentId = UUID.randomUUID().toString();
        String sessionId = "a56s72D9eD";

        watcherRegistry.register(contentId, sessionId);

        assertThat(checkContainedChannelIdInList(sessionId, contentId)).isTrue();
        assertThat(checkContainedSessionIdInList(contentId, sessionId)).isTrue();

        watcherRegistry.unregister(sessionId);

        assertThat(checkContainedChannelIdInList(sessionId, contentId)).isFalse();
        assertThat(checkContainedSessionIdInList(contentId, sessionId)).isFalse();
    }


    @Test
    @DisplayName("현재 컨텐츠를 시청하고 있는 사용자(세션) 수 조회")
    void unRegisterWatcher(){
        mockupLockSet();

        String contentId = UUID.randomUUID().toString();
        String session1 = "a56s72D9eD";
        String session2 = "a56172D9eD";
        String session3 = "af1s72D9eD";

        watcherRegistry.register(contentId, session1);
        watcherRegistry.register(contentId, session2);
        watcherRegistry.register(contentId, session3);



        assertThat(watcherRegistry.getWatcherCount(contentId)).isEqualTo(3L);
    }


}
