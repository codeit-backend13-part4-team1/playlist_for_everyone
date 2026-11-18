package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;

/**
 * 세션 정보 (SessionInfo) 를 받아와서 String 문자열과 로직을 이용한 비교를 하는 룰.
 * 기본적으론 SessionId 가 존재한다면, 매칭을 성공한 것으로 판단한다.
 * 별도 세션 필드와 매칭한다면, match 로직을 오버라이딩 하여 구현한다.
 */

public abstract class SessionRule<T>
        extends AbstractRule<String, SessionInfo, T>
        implements RuleInterface<SessionInfo, T>
{
    public SessionRule(String sessionId){
        super(sessionId);
    }

    @Override
    abstract public T run(SessionInfo source);

    @Override
    public boolean match(SessionInfo sessionInfo){
        return (sessionInfo.session() != null);
    }
}
