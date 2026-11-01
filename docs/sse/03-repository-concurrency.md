# 3. 저장소와 동시성

`SseEmitterRepository`는 연결 등록·조회·제거 계약입니다.
현재 구현체인 `InMemorySseEmitterRepository`는 실제 HTTP 연결을 가진 emitter를 서버 메모리에 보관합니다.

## 사용자 하나에 연결 여러 개

```text
사용자 A
  ├─ 연결 1 → 탭 1의 emitter
  └─ 연결 2 → 탭 2의 emitter
사용자 B
  └─ 연결 3 → 탭 1의 emitter
```

바깥 Map의 키는 사용자 UUID, 안쪽 Map의 키는 `Connection`입니다.
`Connection`은 연결 UUID를 담는 record로, 단순 UUID보다 역할을 드러내기 위해 사용합니다.
사용자 ID만 있으면 모든 탭을 조회하고, 특정 연결을 지우려면 사용자 ID와 연결 식별자가 모두 필요합니다.

| 계약 | 역할 |
| --- | --- |
| `save(userId, connection, emitter)` | 사용자 아래에 연결을 등록합니다. |
| `findAllByUserId(userId)` | 해당 사용자의 모든 로컬 연결을 조회합니다. |
| `delete(userId, connection)` | 지정한 연결만 제거합니다. |
| `findAll()` | 하트비트·서버 종료 처리에 사용할 로컬 연결들을 조회합니다. |

`SseEmitter`에는 실제 HTTP 응답과 관련된 상태가 있으므로 Redis에 넣어 다른 서버에서 사용하는 대상이 아닙니다.
서버 간에는 이벤트 데이터를 전달하고, 각 서버가 자신의 emitter를 찾아 전송합니다.

## 등록과 삭제를 `compute`로 묶는 이유

바깥 Map과 안쪽 Map 모두 `ConcurrentHashMap`입니다.
개별 Map 연산이 안전하더라도, 두 Map에 걸친 작업 전체가 자동으로 하나의 연산이 되지는 않습니다.

사용자 Map 조회와 연결 추가를 따로 수행한다면 다음 상황이 가능합니다.

| 순서 | 새 연결 등록 작업 | 마지막 연결 삭제 작업 |
| --- | --- | --- |
| 1 | 기존 사용자 Map을 가져옵니다. | |
| 2 | | 마지막 연결을 지우고, 빈 사용자 Map을 바깥 Map에서 제거합니다. |
| 3 | 가져온 Map에 새 연결을 넣습니다. | |

3번의 Map은 바깥 Map에서 이미 제거되었으므로 새 연결이 등록되어도 조회되지 않을 수 있습니다.
이를 막기 위해 등록과 삭제 모두 **바깥 Map의 같은 사용자 키에 대한 `compute` 안에서** 수행합니다.

```java
sseEmitters.compute(userId, (id, connections) -> {
    Map<Connection, SseEmitter> userConnections = initializeConnections(connections);
    userConnections.put(connection, sseEmitter);
    return userConnections;
});
```

삭제도 같은 방식으로 연결 제거와 빈 사용자 Map 제거를 묶습니다.
해당 사용자 키의 등록·삭제가 중간 단계에서 서로 끼어들지 않도록 하는 구조입니다.
이 범위에 실제 네트워크 전송은 포함하지 않습니다.

`compute` 안에서 `null`을 반환하면 바깥 Map의 해당 사용자 키가 제거됩니다.
연결이 없거나 같은 삭제가 반복되어도 다른 연결은 삭제하지 않습니다.

## 조회 결과를 복사하는 이유와 한계

조회 결과는 `Map.copyOf`로 반환하여 호출자가 저장소의 Map 구조를 직접 바꾸지 못하게 합니다.
호출자가 받은 결과는 이후 연결 추가·삭제로 구조가 바뀌지 않습니다. 단, 내부 emitter 객체의 참조는 공유합니다.

| 조회 | 복사 범위 | 동시 변경 시 보장 범위 |
| --- | --- | --- |
| `findAllByUserId` | 한 사용자의 연결 Map | `compute` 밖에서 복사하므로, 복사 도중 상태를 한 시점으로 고정하지 않습니다. |
| `findAll` | 사용자별 연결 Map과 결과의 바깥 Map | 각 사용자 복사는 `computeIfPresent` 안에서 하지만, 전체 사용자 조회를 하나의 시점으로 고정하지는 않습니다. |

따라서 `findAll()`이 반환한 뒤 새로 생긴 연결이나 조회 중 추가된 사용자는 결과에서 빠질 수 있습니다.
조회한 emitter가 직후 종료될 수도 있어, 전송 단계에는 별도의 실패 처리가 필요합니다.
복사본의 구조가 바뀌지 않는다는 것과 모든 연결이 동일한 순간의 상태라는 것은 보장 범위가 다릅니다.

## 테스트를 읽을 때

저장소 테스트는 구현체를 실제로 만들어 등록·조회·삭제 결과를 확인합니다.
서비스 테스트의 Mock 저장소는 이 저장 동작을 검증하지 않고, 서비스가 계약대로 호출하는지를 확인합니다.

동시 등록·삭제 테스트는 `CyclicBarrier`로 두 작업의 시작 시점을 맞추고 새 연결이 유지되는지 확인합니다.
`Future.get`으로 작업 완료와 예외를 확인하며 대기 시간을 제한합니다.
한 번의 실행으로 가능한 모든 스레드 순서를 검증한다는 의미는 아닙니다.

## 관련 코드와 테스트

- [SseEmitterRepository](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/repository/SseEmitterRepository.java), [Connection](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/event/Connection.java): 저장 계약과 연결 식별자입니다.
- [InMemorySseEmitterRepository](../../src/main/java/com/codeit/sb13/playlist_for_everyone/global/sse/repository/impl/InMemorySseEmitterRepository.java): 등록·삭제의 원자적 처리와 조회 사본을 구현합니다.
- [InMemorySseEmitterRepositoryTest](../../src/test/java/com/codeit/sb13/playlist_for_everyone/global/sse/repository/impl/InMemorySseEmitterRepositoryTest.java): 저장 결과, 변경 불가 사본, 동시 등록·삭제를 검증합니다.

---

[← 이전: 이벤트 전송과 하트비트](02-delivery-heartbeat.md) · [목차](README.md) · [다음: 비동기 응답과 인증 →](04-async-security.md)
