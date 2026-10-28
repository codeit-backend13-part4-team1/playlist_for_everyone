package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SSE 연결 서비스")
class SseConnectionServiceTest {

    @Test
    @DisplayName("연결을 요청하면 반환된 에미터가 해당 사용자의 저장소에 등록된다.")
    void connectRegisterReturnedEmitter(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                Duration.ofMinutes(30)
        );
        SseConnectionService service = new SseConnectionService(repository, properties);

        UUID userId = UUID.randomUUID();
        SseEmitter emitter = service.connect(userId);

        assertThat(emitter).isNotNull();
        assertThat(repository.findAllByUserId(userId))
                .hasSize(1)
                .containsValue(emitter);
    }

    @Test
    @DisplayName("연결을 요청하면 설정한 연결 제한 시간이 에미터에 적용된다.")
    void  connectAppliesConfiguredTimeout(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        Duration connectionTimeout = Duration.ofMinutes(5);
        SseProperties properties = new SseProperties(
                Duration.ofSeconds(15),
                connectionTimeout
        );

        SseConnectionService service = new SseConnectionService(repository, properties);

        UUID userId = UUID.randomUUID();
        SseEmitter emitter = service.connect(userId);

        assertThat(emitter.getTimeout())
                .isEqualTo(connectionTimeout.toMillis());
    }

    @Test
    @DisplayName("같은 사용자가 두 번 연결하면 서로 다른 에미터가 생성되고 두 연결이 모두 유지된다.")
    void connectTwiceKeepsBothConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);

        UUID userId = UUID.randomUUID();
        SseEmitter firstEmitter = service.connect(userId);
        SseEmitter secondEmitter = service.connect(userId);

        assertThat(secondEmitter).isNotSameAs(firstEmitter);
        assertThat(repository.findAllByUserId(userId))
                .hasSize(2)
                .containsValues(firstEmitter, secondEmitter);
    }

    @Test
    @DisplayName("서로 다른 사용자가 연결하면 각 사용자에게 자신의 연결만 등록된다.")
    void connectSeparatesConnectionsByUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();


        SseEmitter firstEmitter = service.connect(firstUserId);
        SseEmitter secondEmitter = service.connect(secondUserId);

        assertThat(repository.findAllByUserId(firstUserId))
                .hasSize(1)
                .containsValues(firstEmitter);

        assertThat(repository.findAllByUserId(secondUserId))
                .hasSize(1)
                .containsValues(secondEmitter);


    }

}