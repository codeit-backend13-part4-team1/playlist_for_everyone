package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("특정 연결을 삭제하면 같은 사용자의 다른 연결은 유지된다.")
    void deleteKeepsOtherConnectionsForSameUser(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID userId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(userId, firstConnection, firstEmitter);
        repository.save(userId,secondConnection, secondEmitter);

        repository.delete(userId, firstConnection);

        assertThat(repository.findAllByUserId(userId))
                .hasSize(1)
                .doesNotContainKey(firstConnection)
                .containsEntry(secondConnection, secondEmitter);

    }

    @Test
    @DisplayName("마지막 연결을 삭제하면 해당 사용자의 연결 조회 결과가 비어 있다.")
    void deleteLastConnectionReturnsEmptyMap(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID userId = UUID.randomUUID();

        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter();

        repository.save(userId, connection, emitter);

        repository.delete(userId, connection);

        assertThat(repository.findAllByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("같은 연결을 두 번 삭제해도 다른 연결은 유지된다.")
    void deleteSameConnectionTwiceKeepsOtherConnections(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID userId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(userId, firstConnection, firstEmitter);
        repository.save(userId, secondConnection, secondEmitter);

        repository.delete(userId, firstConnection);
        repository.delete(userId, firstConnection);

        assertThat(repository.findAllByUserId(userId))
                .hasSize(1)
                .doesNotContainKey(firstConnection)
                .containsEntry(secondConnection, secondEmitter);

    }

    @Test
    @DisplayName("연결이 없는 사용자의 삭제요청은 다른 사용자의 연결에 영향을 주지않는다.")
    void deleteForUserWithoutConnectionsDoesNotAffectOtherUsers(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID connectedUserId = UUID.randomUUID();
        UUID unconnectedUserId = UUID.randomUUID();

        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter();

        repository.save(connectedUserId, connection, emitter);

        repository.delete(unconnectedUserId, connection);

        assertThat(repository.findAllByUserId(unconnectedUserId)).isEmpty();
        assertThat(repository.findAllByUserId(connectedUserId))
                .hasSize(1)
                .containsEntry(connection, emitter);
    }

    @Test
    @DisplayName("전체 연결을 조회하면 모든 사용자의 연결이 반환된다.")
    void findAllReturnsConnectionsForAllUsers(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();

        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();

        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(firstUserId, firstConnection, firstEmitter);
        repository.save(secondUserId, secondConnection, secondEmitter);

        Map<UUID, Map<Connection, SseEmitter>> result = repository.findAll();

        assertThat(result)
                .hasSize(2)
                .containsEntry(firstUserId, Map.of(firstConnection, firstEmitter))
                .containsEntry(secondUserId, Map.of(secondConnection, secondEmitter));
    }

    @Test
    @DisplayName("전체 연결 조회 결과의 외부Map과 사용자별 연결 Map은 수정 할 수 없다.")
    void findAllReturnsUnmodifiableMaps(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID userId = UUID.randomUUID();
        Connection connection = Connection.create();
        SseEmitter emitter = new SseEmitter();

        repository.save(userId, connection, emitter);

        Map<UUID, Map<Connection, SseEmitter>> result = repository.findAll();

        assertThatThrownBy(()-> result.remove(userId))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(()-> result.get(userId).remove(connection))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(repository.findAllByUserId(userId))
                .containsExactlyEntriesOf(Map.of(connection, emitter));
    }

    @Test
    @DisplayName("전체 연결 조회 후 연결을 추가·삭제해도 기존 조회 결과는 유지된다.")
    void findAllSnapshotIsUnaffectedByConnectionChanges(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        UUID userId = UUID.randomUUID();
        Connection firstConnection = Connection.create();
        Connection secondConnection = Connection.create();
        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();

        repository.save(userId, firstConnection, firstEmitter);

        Map<UUID, Map<Connection, SseEmitter>> snapshot = repository.findAll();

        repository.save(userId, secondConnection, secondEmitter);
        repository.delete(userId, firstConnection);

        assertThat(snapshot.get(userId))
                .containsExactlyEntriesOf(Map.of(firstConnection, firstEmitter));

        assertThat(repository.findAllByUserId(userId))
                .containsExactlyEntriesOf(Map.of(secondConnection, secondEmitter));


    }

    @Test
    @DisplayName("등록된 연결이 없으면 전체 연결 조회 결과는 빈 Map이다.")
    void findAllReturnsEmptyMapWhenNoConnectionsExist(){
        SseEmitterRepository repository = new InMemorySseEmitterRepository();

        Map<UUID, Map<Connection, SseEmitter>> result = repository.findAll();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("이전 연결 삭제와 새 연결등록을 동시에 실행해도 새 연결은 유지된다.")
    void concurrentDeleteAndSaveKeepsNewConnection() throws ExecutionException, InterruptedException, TimeoutException {
        SseEmitterRepository repository = new InMemorySseEmitterRepository();
        UUID userId = UUID.randomUUID();

        Connection oldConnection = Connection.create();
        Connection newConnection = Connection.create();
        SseEmitter newEmitter = new SseEmitter();

        repository.save(userId, oldConnection, new SseEmitter());
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CyclicBarrier barrier = new CyclicBarrier(2);

        try {
            Future<Object> deleteFuture = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                repository.delete(userId, oldConnection);
                return null;
            });

            Future<?> saveFuture = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                repository.save(userId, newConnection, newEmitter);
                return null;
            });

            deleteFuture.get(5, TimeUnit.SECONDS);
            saveFuture.get(5, TimeUnit.SECONDS);

            assertThat(repository.findAllByUserId(userId))
                    .containsExactlyEntriesOf(Map.of(newConnection, newEmitter));
        } finally {
            executor.shutdownNow();
        }
    }
}