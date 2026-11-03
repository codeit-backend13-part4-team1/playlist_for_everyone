package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * DestinationRule 을 매칭하는 Matcher 구현체.
 * 웹 소켓의 Session 에 대해 DestinationRule 을 매칭하여 publish 할 이벤트를 생성한다.
 * 기본적으로 DestinationRule 객체의 상속 객체를 저장한다.
 * 분기별로 새로운 서로 다른 로직을 저장해야 하므로 prototype scope 로 생성한다.
 */

@Scope("prototype")
@Component
@RequiredArgsConstructor
public class DestinationMatcher
        extends AbstractMatcher<DestinationRule<?>, SessionInfo>
        implements MatcherInterface<DestinationRule<?>, SessionInfo>
{

    private final ApplicationEventPublisher publisher;

    @Override
    public void add(DestinationRule<?> rule) {
        list.add(rule);
    }

    @Override
    protected void call(Object event) {
        if(event != null)
            publisher.publishEvent(event);
    }
}
