package com.codeit.sb13.playlist_for_everyone.global.websocket.entity;

import jakarta.annotation.Nullable;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


/**
 * WebSocketSession 과 해당 세션의 Id 를 가지는 클래스.
 * Principal 의 userId 와 loginSessionId 를 추가적으로 설정 할 수 있다.
 */
public class WSSession {
    private final String id;
    private final WebSocketSession socket;

    // 세션에 대한 구독 정보를 저장하는 Map
    private final Map<String, WSSubscription> subscriptions = new ConcurrentHashMap<>();
    private State state;


    // 세션의 인증 정보
    private String userId;
    private String loginSessionId;

    /*
     * 동시성 시나리오를 위한 상태 클래스
     * Create - 새로운 세션.
     * Register - Connect 프레임이 지나서 연결이 정상적으로 등록된 세션.
     * Destroy - 소켓 연결이 끊기(게 될)세션.
     * 동시성 시나리오에서 세션이 만들어지고, 연결되기 전의 짧은 텀은 삭제를 방지한다.
     */
    private enum State {
        CREATE,
        REGISTER,
        DESTROY
    }


    public WSSession(String id, WebSocketSession socket) {
        this.id = Objects.requireNonNull(id);
        this.socket = Objects.requireNonNull(socket);
        this.state = State.CREATE;
    }

    /**
     * Spring 에서 부여한 고유 식별자.
     * @return String 세션 id
     */
    public String id(){ return id; }

    /**
     * 식별자에 해당하는 WebSocketSession 객체.
     * @return WebSocketSession 객체.
     */
    public WebSocketSession socket(){ return socket; }


    /*
     * 인증 정보에 관한 매서드.
     */

    /**
     * 세션에 대한 인증 정보를 설정한다.
     * @param userId 인증된 사용자 id
     * @param loginSessionId 인증된 세션 id
     */
    public void setAuthInfo(String userId, String loginSessionId) {
        this.userId = userId;
        this.loginSessionId = loginSessionId;
    }

    /**
     * 세션 사용자의 id를 반환한다.
     * @return 인증 정보가 없을 경우 null
     */
    @Nullable public String userId(){ return userId; }

    /**
     * 세션 사용자의 로그인 세션 id를 반환한다.
     * @return 인증 정보가 없을 경우 null
     */
    @Nullable public String loginSessionId(){ return loginSessionId; }


    /*
     * 구독 관련 매서드.
     */

    /**
     * 세션에 등록된 구독을 반환한다.
     * 인자가 없거나, 등록되지 않았으면, 빈 Optional 을 반환한다.
     * @param subscriptionId 구독 Id
     * @return 옵셔넝 구독
     */
    public Optional<WSSubscription> getSubscription(String subscriptionId){
        if (subscriptionId == null) return Optional.empty();
        return Optional.ofNullable(subscriptions.get(subscriptionId));
    }


    /**
     * 세션에 새 구독 을 추가하는 로직.
     * 같은 세션 안에 같은 구독 ID 혹은 같은 경로 가 있을 경우, 구독이 추가되지 않는다.
     */
    public void subscribe(String subscriptionId, String destination, @Nullable Consumer<?> callback){
        if (this.state != State.REGISTER) throw new IllegalStateException("세션에서 구독을 시행할 수 없습니다.");

        WSSubscription subscription = new WSSubscription(subscriptionId, destination, callback);
        subscriptions.compute(subscriptionId, (k, v) -> {
            if (v == null) return subscription;
            throw new IllegalStateException("이미 같은 구독 ID 혹은 경로가 존재합니다.");
        });
    }

    /**
     * 인자에 해당하는 구독을 제거하는 로직.
     */
    public void unsubscribe(String subscriptionId){
        if (this.state != State.REGISTER) throw new IllegalStateException("세션에서 구독을 취소할 수 없습니다.");

        subscriptions.computeIfPresent(subscriptionId, (k, v) -> {

            v.onUnregister();

            return null;
        });
    }

    /**
     * Stomp 의 Connect 에 해당하는 로직.
     * 웹 세션이 완전하게 등록되어있다고 판단, 이후 작업을 수행해도 좋다는 상태를 설정하기 위한 매서드
     * 인증정보의 기록 등을 수행한다.
     */
    public void connect(@Nullable String userId,@Nullable String loginSessionId){
        if (this.state != State.CREATE) throw new IllegalStateException("세션이 이미 등록되었습니다.");

        state(State.REGISTER);

        this.userId = userId;
        this.loginSessionId = loginSessionId;
    }

    /*
     * 세션 연결을 끊을 때 사용하는 매서드
     */

    /**
     * 세션을 종료할 때 실행되는 로직.
     */
    public void disconnect() throws IOException {
        // 삭제 전 생성 방어
        if (this.state == State.CREATE) throw new IllegalStateException("세션이 아직 등록되지 않았습니다.");
        // 중복 삭제 방어
        if (this.state == State.DESTROY) return;

        // 원자성 있게 상태 변경.
        state(State.DESTROY);

        // 현재 구독 리스트를 카피 후, 본 리스트 삭제.
        for (WSSubscription subscription : Set.copyOf(subscriptions.values())) {
            try {
                subscription.onUnregister();
                subscriptions.remove(subscription.id(), subscription);
            } catch (RuntimeException e){
                // 삭제 누락시, 비즈니스 로직에 잔존. 비즈니스 로직 배치 삭제등으로 해결.
                // 에러가 나오더라도 소켓은 삭제 되어야 함. 별도 에러 던지지 않음.
            }
        }

        close();
    }


    private void state(State state){
        synchronized (this) {
            this.state = state;
        }
    }



    private void close() throws IOException {
        socket.close();
    }

}
