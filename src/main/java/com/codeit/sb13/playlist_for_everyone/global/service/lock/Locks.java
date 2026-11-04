package com.codeit.sb13.playlist_for_everyone.global.service.lock;

/**
 * 각 클래스마다 사용할 간단한 락을 제공하는 인터페이스.
 */
public interface Locks {
    /**
     * Object.hashcode() 의 값에 따라 지정된 lock 의 키가 될 객체를 반환하는 매서드.
     * 클래스 필드에 주입 할 때 마다 새로운 클래스를 생성하기 때문에, 서로다른 두
     * 클래스에 주입된 Locks 객체는 같은 해시에 대해 서로 다른 락을 반환한다.
     * @param key 해시값을 추출할 객체
     * @return 락의 키가 될 Object
     */
    Object lock(Object key);
}
