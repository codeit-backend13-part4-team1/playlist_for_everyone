package com.codeit.sb13.playlist_for_everyone.global.websocket.entity;

import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.WebSocketSubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.WebSocketSubscriptionRegistryImpl;
import jakarta.annotation.Nullable;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;


/**
 * WebSocketSession 과 해당 세션의 Id 를 가지는 클래스.
 * Principal 의 userId 와 loginSessionId 를 추가적으로 설정 할 수 있다.
 */
public class WSSession {
    private final String id;
    private final WebSocketSession socket;

    // 세션에 대한 구독 정보를 저장하는 저장소
    private final WebSocketSubscriptionRegistry subscriptions = new WebSocketSubscriptionRegistryImpl();
    private State state;


    // 세션의 인증 정보
    volatile private String userId;
    volatile private String loginSessionId;

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
        return subscriptions.get(subscriptionId);
    }

    /**
     * 세션에 등록된 구독을 반환한다.
     * 인자가 없거나, 등록되지 않았으면, 빈 Optional 을 반환한다.
     * @param subscriptionUUID 구독 uuid
     * @return 옵셔넝 구독
     */
    public Optional<WSSubscription> getSubscription(UUID subscriptionUUID){
        if (subscriptionUUID == null) return Optional.empty();
        return subscriptions.get(subscriptionUUID);
    }

    /**
     * 세션에 등록된 구독을 반환한다.
     * 인자가 없거나, 등록되지 않았으면, 빈 리스트 을 반환한다.
     * @return 세션에 해당된 모든 구독 리스트
     */
    public List<WSSubscription> getAllSubscriptions(){
        return subscriptions.getAll();
    }


    /**
     * 세션에 새 구독 을 추가하는 로직.
     * 같은 세션 안에 같은 구독 ID 혹은 가 있을 경우, 구독이 추가되지 않는다.
     */
    public synchronized WSSubscription subscribe(String subscriptionId, String destination){
        Objects.requireNonNull(subscriptionId);
        Objects.requireNonNull(destination);
        if (state != State.REGISTER) throw new IllegalStateException("세션에서 구독을 시행할 수 없습니다.");

        return subscriptions.add(new WSSubscription(id ,subscriptionId, destination));
    }

    /**
     * 인자에 해당하는 구독을 제거하는 로직.
     */
    public synchronized void unsubscribe(WSSubscription subscription){
        Objects.requireNonNull(subscription);
        if (state != State.REGISTER) throw new IllegalStateException("세션에서 구독을 취소할 수 없습니다.");

        subscriptions.remove(subscription);
    }

    /**
     * Stomp 의 Connect 에 해당하는 로직.
     * 웹 세션이 완전하게 등록되어있다고 판단, 이후 작업을 수행해도 좋다는 상태를 설정하기 위한 매서드
     * 인증정보의 기록 등을 수행한다.
     */
    public synchronized void connect(@Nullable String userId,@Nullable String loginSessionId){
        if (state != State.CREATE) throw new IllegalStateException("세션이 이미 등록되었습니다.");

        this.userId = userId;
        this.loginSessionId = loginSessionId;

        state = State.REGISTER;
    }

    /*
     * 세션 연결을 끊을 때 사용하는 매서드
     */

    /**
     * 세션을 종료할 때 실행되는 로직.
     */
    public void disconnect() throws IOException {
        synchronized (this){
            if (state == State.DESTROY) return;
            state = State.DESTROY;
            // 구독 전부 삭제
            subscriptions.clear();
        }
        close();
    }


    private void close() throws IOException {
        socket.close();
    }

}
