package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import java.util.HashSet;
import java.util.Set;

/**
 * Rule 을 리스트로 저장, key 와 매칭되는 Rule 일 경우
 * Rule 에 세팅된 run 매서드를 실행하는 추상 클래스
 * @param <T> Rule. 매치를 위한 패턴과, 실행할 로직을 담고있음.
 * @param <U> Key. rule 의 패턴과 매칭을 시도할 값.
 */

public abstract class AbstractMatcher<T extends AbstractRule<?,U,?>,U> {
    protected final Set<T> list = new HashSet<>();

    abstract public void add(T rule);

    abstract protected void call(Object event);

    public void run(U key){
        for (T rule : list){
            if (rule.match(key)){
                call(rule.run(key));
            }
        }
    }

}
