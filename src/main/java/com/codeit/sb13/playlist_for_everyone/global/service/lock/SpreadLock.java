package com.codeit.sb13.playlist_for_everyone.global.service.lock;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.stream.IntStream;

/**
 * 간단한 SpreadLock 구현체.
 * 현재 락 객체 컨테이너가 non-static으로, 의존성 주입되거나 pojo 생성 시,
 * 새로운 락 컨테이너와 객체를 생성한다.
 * 때문에, 서로다른 클래스의 필드에 생성된 락 객체는 같은 해시에 서로 다른 락을 반환한다.
 * -> 측정 후, 락 충돌이 충분히 적다고 판단이 될 경우, 락 컨테이너를 static 으로 변경하면
 * 어플리케이션 전역적 락으로 변경 가능.
 */
@Scope("prototype")
@Component
public class SpreadLock implements Locks {
    private final int LOCK_SIZE = 2048;
    private final Object[] LOCKS = IntStream.range(0, LOCK_SIZE)
            .mapToObj(i -> new Object()).toArray();


    public Object lock(Object key){
        int hash = key.hashCode();
        int spread = hash ^ (hash >>> 16);
        return LOCKS[spread & (LOCK_SIZE - 1)];
    }
}
