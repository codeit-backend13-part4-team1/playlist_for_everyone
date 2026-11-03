package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Session 정보 를 기준으로 매칭을 행하는 Matcher 구현체.
 * SessionInfo 의 특정 필드가 SessionRule 의 Match 로직에 true 를 반환하면,
 * 해당 Rule 의 이벤트를 발행한다.
 */

@Scope("prototype")
@Component
@RequiredArgsConstructor
public class SessionMatcher
        extends AbstractMatcher<SessionRule<?>, SessionInfo>
        implements MatcherInterface<SessionRule<?>, SessionInfo>
{
    private final ApplicationEventPublisher publisher;

    @Override
    public void add(SessionRule<?> rule){ list.add(rule);}

    @Override
    protected void call(Object event){
        if (event != null) publisher.publishEvent(event);
    }
}
