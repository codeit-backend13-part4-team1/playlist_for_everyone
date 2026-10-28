package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

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

        Assertions.assertThat(repository.findAllByUserId(userId))
                .containsEntry(connection, emitter);

    }

}