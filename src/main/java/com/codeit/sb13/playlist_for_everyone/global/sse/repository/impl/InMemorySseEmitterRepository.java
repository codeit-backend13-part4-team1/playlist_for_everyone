package com.codeit.sb13.playlist_for_everyone.global.sse.repository.impl;

import com.codeit.sb13.playlist_for_everyone.global.sse.event.Connection;
import com.codeit.sb13.playlist_for_everyone.global.sse.repository.SseEmitterRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 ID -> 연결 ID -> SseEmitter 구조로 현재 인스턴스의 연결을 보관한다.
 *
 * <p>실제 HTTP 연결을 가진 emitter는 다른 서버로 복제할 수 있는 메시지가 아니다.
 * Redis/Kafka로 이벤트를 공유하더라도 emitter는 연결이 맺어진 서버의 메모리에서 관리한다.
 */
@Repository
public class InMemorySseEmitterRepository implements SseEmitterRepository {

    // 바깥 Map은 사용자별 등록·삭제를 묶고, 안쪽 Map은 개별 조회 중의 동시 변경에도 대응한다.
    private final Map<UUID, Map<Connection, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    @Override
    public void save(UUID userId, Connection connection, SseEmitter sseEmitter) {
      // 사용자 Map 생성과 연결 추가를 같은 compute 안에서 수행한다.
      // 마지막 연결 삭제가 사용자 Map을 제거하는 순간, 별도로 추가한 새 연결이 함께 유실되는 경합을 막는다.
      sseEmitters.compute(userId, (id, connections)->{
          Map<Connection, SseEmitter> userConnections = initializeConnections(connections);
          userConnections.put(connection, sseEmitter);
          return userConnections;
      });
    }

    @Override
    public Map<Connection, SseEmitter> findAllByUserId(UUID userId) {
        // 호출자가 저장소의 Map을 직접 변경하지 못하게 구조만 복사한다. emitter 참조는 공유한다.
        // 이 조회는 compute로 묶지 않으므로 복사 중의 동시 변경 전체를 한 시점의 상태로 보장하지 않는다.
        return Map.copyOf(sseEmitters.getOrDefault(userId, Map.of()));
    }

    @Override
    public void delete(UUID userId, Connection connection) {
        // 연결 제거와 빈 사용자 Map 정리를 save와 같은 키의 원자적 변경 안에서 처리한다.
        sseEmitters.compute(userId, (id, connections)->{
            if (connections == null){
                return null;
            }
            connections.remove(connection);
            return removeEmptyConnections(connections);
        });
    }

    @Override
    public Map<UUID, Map<Connection, SseEmitter>> findAll() {
        Map<UUID, Map<Connection, SseEmitter>> result = new HashMap<>();
        // 사용자별 사본은 해당 키의 compute 안에서 만들어 같은 사용자의 save/delete와 겹치지 않게 한다.
        // 사용자들은 순차 조회하므로 전체가 하나의 원자적 스냅샷은 아니다. 동시 신규 연결은 다음 조회에 잡힐 수 있다.
        sseEmitters.keySet()
                .forEach(userId -> sseEmitters.computeIfPresent(userId,
                        (id, connections) -> {
                    result.put(userId, Map.copyOf(connections));
                    return connections;
                }));

        return Map.copyOf(result);
    }

    private Map<Connection, SseEmitter> removeEmptyConnections(Map<Connection, SseEmitter> connections) {
        if (connections == null || connections.isEmpty()){
            // ConcurrentHashMap.compute에서 null 반환은 해당 사용자 키를 제거한다는 뜻이다.
            return null;
        }
        return connections;
    }

    private Map<Connection, SseEmitter> initializeConnections(Map<Connection, SseEmitter> connections) {
        if (connections == null){
            return new ConcurrentHashMap<>();
        }
        return connections;
    }

}
