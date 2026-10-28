package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("메모리 SSE 연결 저장소")
class InMemorySseEmitterRepositoryTest {

    @Test
    @DisplayName("연결을 등록하면 해당 사용자의 연결 목록에서 조회된다.")
    void saveAndFindAllByUserId(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();

        SseEmitter emitter = new SseEmitter();
        repository.save(userId, connection, emitter);

        assertThat(repository.findAllByUserId(userId))
                .containsEntry(connection, emitter);

    }

    @Test
    @DisplayName("같은 사용자의 연결을 두 개 등록하면 기존 연결과 새 연결이 모두 유지된다.")
    void saveMultipleConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID userId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(userId, firstConnection, firstEmitter);
        repository.save(userId, secondConnection, secondEmitter);

        assertThat(repository.findAllByUserId(userId))
                .hasSize(2)
                .containsEntry(firstConnection, firstEmitter)
                .containsEntry(secondConnection, secondEmitter);


    }

    @Test
    @DisplayName("사용자별 연결을 조회하면 해당 사용자의 연결만 반환한다.")
    void findAllByUserIdReturnsOnlyOwnConnections(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(firstUserId, firstConnection, firstEmitter);
        repository.save(secondUserId, secondConnection, secondEmitter);

        assertThat(repository.findAllByUserId(firstUserId))
                .hasSize(1)
                .containsEntry(firstConnection, firstEmitter);

        assertThat(repository.findAllByUserId(secondUserId))
                .hasSize(1)
                .containsEntry(secondConnection, secondEmitter);
    }

    @Test
    @DisplayName("연결이 없는 사용자를 조회하면 빈 Map을 반환한다.")
    void findAllByUserIdReturnsEmptyMapWhenUserHasNoConnections(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID connectedUserId = UUID.randomUUID();
        UUID unconnectedUserId = UUID.randomUUID();

        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter();

        repository.save(connectedUserId, connection, emitter);

        assertThat(repository.findAllByUserId(unconnectedUserId)).isEmpty();
    }
}