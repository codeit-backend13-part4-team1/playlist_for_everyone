package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.repository;


import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatcherScriptConfig;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.STORAGE_CONTENT_PREFIX;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.STORAGE_SESSION_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled // 스크립트 로그 테스트 용. 필요시 활성화.
@Import(WatcherScriptConfig.class)
public class WatchingSessionRepositoryPRT {

    LettuceConnectionFactory factory;
    StringRedisTemplate template;

    WatchingSessionRepository repository;
    WatcherScriptConfig script;

    @BeforeEach
    void setup(){
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName("localhost");
        configuration.setPort(6379);

        factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();

        template = new StringRedisTemplate(factory);

        script = new WatcherScriptConfig();

        repository = new WatchingSessionRepository(
                template,
                script.watcherRegisterScript(),
                script.watcherUnregisterScript(),
                script.watcherRefreshScript(),
                script.watcherDeleteScript(),
                script.watcherCountScript(),
                script.watcherDeleteWithContentIdScript()
        );

        register();
    }

    String contentId1 = UUID.randomUUID().toString();
    String contentId2 = UUID.randomUUID().toString();
    String sessionId1 = "test-session-id1";
    String sessionId2 = "test-session-id2";
    String subscriptionId1 = "test-sub-1";
    String subscriptionId2 = "test-sub-2";

    void register(){
        // content 1 에는 session 1,2
        repository.register(sessionId1, subscriptionId1, contentId1);
        repository.register(sessionId2, subscriptionId1, contentId1);

        // content 2 에는 session 1 만
        repository.register(sessionId1, subscriptionId2, contentId2);
    }


    @AfterEach
    void clean(){
        clear();
        factory.destroy();
    }

    private void clear(){
        Set<String> sessions = template.keys(STORAGE_SESSION_PREFIX + "*");
        Set<String> contents = template.keys(STORAGE_CONTENT_PREFIX + "*");
        contents.addAll(sessions);
        for(String key : contents){
            template.delete(key);
        }
    }

    @Test
    @DisplayName("컨텐츠 id 기반 삭제 테스트")
    void deleteWithContentIdTest(){
        repository.deleteWithContentId(UUID.fromString(contentId1));

        assertThat(repository.getWatcherCount(contentId1)).isEqualTo(0L);
        assertThat(repository.getWatcherCount(contentId2)).isEqualTo(1L);
    }


}
