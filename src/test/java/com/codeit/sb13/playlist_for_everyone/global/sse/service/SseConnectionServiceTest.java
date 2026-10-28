package com.codeit.sb13.playlist_for_everyone.global.sse.service;

import com.codeit.sb13.playlist_for_everyone.global.sse.config.SseProperties;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl.InMemorySseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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

    @Test
    @DisplayName("연결 완료 콜백이 실행되면 해당 연결이 저장소에서 제거된다.")
    void completionCallbackRemovesConnection(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);

        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter emitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId)).containsValue(emitter);

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

            verify(emitter).onCompletion(completionCaptor.capture());
            completionCaptor.getValue().run();

            assertThat(repository.findAllByUserId(userId)).isEmpty();
        }
    }

    @Test
    @DisplayName("한 연결의 완료 콜백이 실행돼도 같은 사용자의 다른 연결은 유지된다.")
    void completionCallbackKeepsOtherConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(2)
                    .containsValues(firstEmitter, secondEmitter);

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onCompletion(completionCaptor.capture());

            completionCaptor.getValue().run();

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(1)
                    .doesNotContainValue(firstEmitter)
                    .containsValue(secondEmitter);
        }
    }

    @Test
    @DisplayName("한 사용자의 연결이 완료돼도 다른 사용자의 연결은 유지된다.")
    void  completionCallbackKeepsOtherUsersConnections(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)){
            SseEmitter firstEmitter = service.connect(firstUserId);
            SseEmitter secondEmitter = service.connect(secondUserId);

            assertThat(repository.findAllByUserId(firstUserId)).containsValue(firstEmitter);

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

            verify(firstEmitter).onCompletion(completionCaptor.capture());
            completionCaptor.getValue().run();

            assertThat(repository.findAllByUserId(firstUserId)).isEmpty();
            assertThat(repository.findAllByUserId(secondUserId))
                    .hasSize(1)
                    .containsValue(secondEmitter);
        }

    }

    @Test
    @DisplayName("이전 연결의 완료 콜백이 다시 실행돼도 새 연결은 유지된다.")
    void repeatedCompletionCallbackKeepsNewConnection(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId)).containsValue(oldEmitter);

            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(oldEmitter).onCompletion(completionCaptor.capture());
            Runnable oldCompletion = completionCaptor.getValue();
            oldCompletion.run();

            assertThat(repository.findAllByUserId(userId)).isEmpty();

            SseEmitter newEmitter = service.connect(userId);
            oldCompletion.run();

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(1)
                    .doesNotContainValue(oldEmitter)
                    .containsValue(newEmitter);
        }

    }

    @Test
    @DisplayName("연결 시간이 초과되면 해당연결을 제거하고 응답 종료를 요청한다.")
    void timeoutCallbackRemovesConnectionAndCompletesEmitter(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId)).containsValue(emitter);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(timeoutCaptor.capture());
            timeoutCaptor.getValue().run();

            assertThat(repository.findAllByUserId(userId)).isEmpty();
            verify(emitter).complete();
        }
    }

    @Test
    @DisplayName("한 연결이 타임아웃돼도 같은 사용자의 다른 연결은 유지되고 종료되지 않는다.")
    void timeoutCallbackKeepsOtherConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(2)
                    .containsValues(firstEmitter, secondEmitter);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(firstEmitter).onTimeout(timeoutCaptor.capture());
            timeoutCaptor.getValue().run();

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(1)
                    .doesNotContainValue(firstEmitter)
                    .containsValue(secondEmitter);

            verify(firstEmitter).complete();
            verify(secondEmitter, never()).complete();

        }
    }

    @Test
    @DisplayName("타임아웃된 연결의 완료 콜백이 실행돼도 새 연결은 유지된다.")
    void completionAfterTimeoutKeepsNewConnection(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter oldEmitter = service.connect(userId);
            assertThat(repository.findAllByUserId(userId)).containsValue(oldEmitter);

            ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
            ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

            verify(oldEmitter).onTimeout(timeoutCaptor.capture());
            verify(oldEmitter).onCompletion(completionCaptor.capture());

            timeoutCaptor.getValue().run();
            assertThat(repository.findAllByUserId(userId)).isEmpty();
            verify(oldEmitter).complete();

            SseEmitter newEmitter = service.connect(userId);
            completionCaptor.getValue().run();

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(1)
                    .doesNotContainValue(oldEmitter)
                    .containsValue(newEmitter);

            verify(newEmitter, never()).complete();

        }
    }


    @Test
    @DisplayName("연결 오류 콜백이 실행되면 해당 연결이 저장소에서 제거된다.")
    void errorCallbackRemovesConnection(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId)).containsValue(emitter);

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();
            verify(emitter).onError(errorCaptor.capture());

            errorCaptor.getValue().accept(new IOException("연결 오류"));

            assertThat(repository.findAllByUserId(userId)).isEmpty();


        }
    }
    
    @Test
    @DisplayName("한 연결에서 오류가 발생해도 같은 사용자의 다른 연결은 유지되고 종료되지 않는다.")
    void errorCallbackKeepsOtherConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        SseProperties properties = new SseProperties(Duration.ofSeconds(15), Duration.ofMinutes(30));

        SseConnectionService service = new SseConnectionService(repository, properties);
        UUID userId = UUID.randomUUID();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class)) {
            SseEmitter firstEmitter = service.connect(userId);
            SseEmitter secondEmitter = service.connect(userId);

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(2)
                    .containsValues(firstEmitter, secondEmitter);

            ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.captor();

            verify(firstEmitter).onError(errorCaptor.capture());
            errorCaptor.getValue().accept(new IOException("연결 오류"));

            assertThat(repository.findAllByUserId(userId))
                    .hasSize(1)
                    .doesNotContainValue(firstEmitter)
                    .containsValue(secondEmitter);

            verify(secondEmitter, never()).complete();
            verify(secondEmitter, never()).completeWithError(any());
        }
    }
}