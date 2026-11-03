package com.codeit.sb13.playlist_for_everyone.global.service.redis;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.Objects;
import java.util.UUID;

/**
 * 기본적으로 Redis 토픽을 구독하기 위한 메세지 리스너 클래스
 * 정확한 토픽을 구독했을 때, 정확히 의도한 객체가 올 수 있다.
 * 사용자 로직은 run 매서드를 오버라이딩 해서 작성하면 된다.
 */
@Slf4j
public abstract class RedisMessageListener implements MessageListener {

    private final UUID id;
    private final RedisTemplate<String, Object> redis;

    public RedisMessageListener(
            RedisTemplate<String, Object> redis
    ) {
        super();
        this.id = UUID.randomUUID();
        this.redis = redis;
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            run(deserialize(message));
        } catch (RuntimeException e){
            log.error("[{}] redis 메세지 로직 실행 중 오류 발생.",
                    this.getClass().getSimpleName()
                    , e
            );
        }

    }

    private Object deserialize(@NonNull Message message){
        return Objects.requireNonNull(redis.getDefaultSerializer()).deserialize(message.getBody());
    }

    /**
     * 역 직렬화 한 데이터를 이용한 후속 처리 매서드.
     * 커스텀 로직을 작성 할 때, 이 로직을 오버라이딩 해서 사용하면 된다.
     * @param data 역직렬화 된 데이터
     */
    protected abstract void run(Object data);

    /**
     * @return 각 리스너의 고유 id
     */
    public UUID id(){ return id; }

    @Override
    public int hashCode(){
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof RedisMessageListener listener)) return false;
        return Objects.equals(id, listener.id);
    }

}
