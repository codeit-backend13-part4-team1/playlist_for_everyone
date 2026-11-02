package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import java.util.regex.Pattern;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

/**
 * 정규표현식을 패턴으로 받아 SessionInfo(웹 소켓) 의 destination 과 매칭을 시도하는 Rule 추상 매서드.
 * 매칭이 성공하면, Source -> T 를 반환하는 로직을 실행한다.
 * @param <T> 매칭이 성공하면 publish 할 이벤트 객체.
 */

public abstract class DestinationRule<T>
        extends AbstractRule<Pattern, SessionInfo,T>
        implements DestinationRuleInterface<SessionInfo,T>
{

    public DestinationRule(Pattern pattern) {
        super(pattern);
    }

    @Override
    abstract public T run(SessionInfo source);

    @Override
    public boolean match(SessionInfo source) {
        if (source == null) return false;
        String destination = source.destination();
        return destination != null && pattern.matcher(destination).matches();
    }
}
