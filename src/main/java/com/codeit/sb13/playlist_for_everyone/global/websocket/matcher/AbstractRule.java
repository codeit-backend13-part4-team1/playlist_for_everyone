package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

/**
 * 특정 패턴이 될 객체를 저장하고, source 와 매칭을 시도하는 추상 클래스
 * @param <T> Rule. 매치를 위한 패턴과, 실행할 로직을 담고있음.
 * @param <U> Key. rule 의 패턴과 매칭을 시도할 값.
 * @param <R> Rule. 매치를 위한 패턴과, 실행할 로직을 담고있음.
 */

public abstract class AbstractRule<T,U,R> {

    protected final T pattern;

    protected AbstractRule(T pattern){
        this.pattern = pattern;
    }

    abstract public R run(U source);

    abstract public boolean match(U source);
}
