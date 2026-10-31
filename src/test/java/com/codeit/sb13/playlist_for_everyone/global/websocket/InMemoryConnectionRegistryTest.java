package com.codeit.sb13.playlist_for_everyone.global.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/*
 * 세션 - 구독 키 쌍을 저장하는 구현체를 직접 호출해서 테스트 한다.
 * 세션 - 구독 키 쌍을 나타내는 SocketConnection 객체는 필드가 변환되지 않으므로, 동시성 테스트는 진행하지 않는다.
 */
@DisplayName("세션-커넥션 키 쌍 테스트")
public class InMemoryConnectionRegistryTest {

    @Test
    @DisplayName("세션 정보를 등록하고 조회하면, id 와 등록 시간이 포함된 SocketConnection 객체가 반환된다.")
    void saveSession(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);

        SocketConnection connection = registry.get("test-session-id", null);

        assertThat(connection).isNotNull();
        assertThat(connection.session()).isEqualTo("test-session-id");
    }

    @Test
    @DisplayName("세션 정보 없이 세션-구독 쌍을 등록하면 에러를 반환한다.")
    void saveSessionWithoutSession(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        assertThatThrownBy(() -> registry.register("test-session","sub-1",null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("동일한 세션 - 구독 키를 복수회 등록하면 최초의 세션 정보만 등록된다.")
    void saveDurationSessionWithSameKey(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);

        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-1",null);


        assertThat(registry.get("test-session-id"))
                .hasSize(1)
                // SocketConnection 은 session - subscribe 쌍이 같다면 동일한 객체로 간주한다.
                .contains(new SocketConnection("test-session-id","sub-1",null));
    }


    // 조회 테스트

    @Test
    @DisplayName("세션 - 구독 쌍을 조회하면 해당 세션, 구독에 해당하는 SocketConnection 객체가 반환된다.")
    void getSession(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id2",null,null);

        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);
        registry.register("test-session-id2","sub-1",null);

        assertThat(registry.get("test-session-id","sub-1"))
                .isEqualTo(new SocketConnection("test-session-id","sub-1",null));
    }

    @Test
    @DisplayName("세션 만 조회하면, 해당 세션과 세션에 연결된 구독 쌍을 모두 반환한다.")
    void getSessionList(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id2",null,null);

        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);
        registry.register("test-session-id2","sub-1",null);

        assertThat(registry.get("test-session-id"))
                .hasSize(3)
                .contains(new SocketConnection("test-session-id",null,null))
                .contains(new SocketConnection("test-session-id","sub-1",null))
                .contains(new SocketConnection("test-session-id","sub-2",null));
    }

    @Test
    @DisplayName("세션 - null 을 조회하면, 해당 세션만 반환한다.")
    void getSession2(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id2",null,null);

        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);
        registry.register("test-session-id2","sub-1",null);

        assertThat(registry.get("test-session-id",null))
                .isEqualTo(new SocketConnection("test-session-id",null,null));
    }

    @Test
    @DisplayName("없는 세션 - 구독 쌍을 조회하면 null 이 반환된다")
    void getSession3() {
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        assertThat(registry.get("test-session-id",null))
                .isNull();
    }

    // 삭제 테스트

    @Test
    @DisplayName("세션 - 구독 키로 특정 세션을 삭제할 수 있다.")
    void removeSession(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);

        registry.unregister("test-session-id","sub-1");

        assertThat(registry.get("test-session-id"))
                .hasSize(2)
                .contains(new SocketConnection("test-session-id",null,null))
                .contains(new SocketConnection("test-session-id","sub-2",null));
    }

    @Test
    @DisplayName("세션 - null 을 삭제하면 세션과 세션에 연관된 모든 구독들이 같이 삭제된다.")
    void removeSession2(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);

        registry.unregister("test-session-id", null);

        assertThat(registry.get("test-session-id"))
                .isNull();
    }

    @Test
    @DisplayName("세션 을 삭제하면 세션과 세션에 연관된 모든 구독들이 같이 삭제된다.")
    void removeSession3(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        registry.register("test-session-id",null,null);
        registry.register("test-session-id","sub-1",null);
        registry.register("test-session-id","sub-2",null);

        registry.unregister("test-session-id");

        assertThat(registry.get("test-session-id"))
                .isNull();
    }

    @Test
    @DisplayName("없는 세션을 삭제하려 하면 예외가 발생한다")
    void removeSession4(){
        InMemoryConnectionRegistry registry = new InMemoryConnectionRegistry();

        assertThatThrownBy(() -> registry.unregister("test-session-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }



}
