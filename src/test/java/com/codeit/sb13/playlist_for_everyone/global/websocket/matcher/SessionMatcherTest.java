package com.codeit.sb13.playlist_for_everyone.global.websocket.matcher;

import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockingDetails;


@ExtendWith(MockitoExtension.class)
@DisplayName("웹 소켓 세션 매쳐 테스트")
public class SessionMatcherTest {
    // 테스트를 위한 이벤트 구현체.
    @Getter
    static abstract class TestEvent {
        String eventName;
        public TestEvent(String eventName){
            this.eventName = eventName;
        }
    }

    static class TestEvent1 extends TestEvent {
        public TestEvent1(String name) { super(name); }
    }
    static class TestEvent2 extends TestEvent {
        public TestEvent2(String name) { super(name); }
    }

    // 테스트를 위한 룰 구현체.
    // run 매서드에 반환 이벤트를 구축해서 반환한다.
    static class TestRuleImpl1 extends SessionRule<TestEvent1>{
        String eventName;
        public TestRuleImpl1(String pattern, String name){
            super(pattern);
            eventName = name;
        }

        @Override
        public TestEvent1 run(SessionInfo sessionInfo){
            return new TestEvent1(eventName);
        }
    }

    // custom 매치 로직을 위한 테스트 클래스
    static class TestRuleImpl2 extends SessionRule<TestEvent2>{
        String eventName;
        public TestRuleImpl2(String pattern, String name){
            super(pattern);
            eventName = name;
        }

        // 구독 id 와 매칭하는 커스텀 로직.
        @Override
        public boolean match(SessionInfo sessionInfo){
            String sub = sessionInfo.subscription();
            return sub != null && sub.equals(pattern);
        }

        @Override
        public TestEvent2 run(SessionInfo sessionInfo){
            return new TestEvent2(eventName);
        }
    }

    @Mock
    ApplicationEventPublisher publisher;

    SessionMatcher testMatcher;

    // 테스트 용 subscribe 이벤트 반환. 이벤트 포맷은 동일하므로, 대표로 subscribe 를 사용.
    SessionInfo getInfo(String destination){
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null)
            accessor.setDestination(destination);
        accessor.setSessionId("test-session");
        accessor.setSubscriptionId("test-sub");

        Message<byte[]> message = MessageBuilder
                .createMessage("test".getBytes(StandardCharsets.US_ASCII), accessor.getMessageHeaders());

        return new SessionInfo(message, null);
    }

    List<TestEvent> getEvents(){
        return mockingDetails(publisher)
                .getInvocations()
                .stream()
                .filter(inv -> inv.getMethod().getName().equals("publishEvent"))
                .map(inv -> (TestEvent) inv.getArgument(0))
                .toList();
    }

    @BeforeEach
    void setup(){
        testMatcher = new SessionMatcher(publisher);
    }

    @Test
    @DisplayName("세션 정보를 인자로 받아서 이벤트를 저장, 발행할 수 있다.")
    void matchTest(){
        TestRuleImpl1 rule = new TestRuleImpl1("test", "test1");

        testMatcher.add(rule);

        testMatcher.run(getInfo("destination"));

        List<TestEvent> events = getEvents();
        assertThat(events).extracting(TestEvent::getEventName)
                .contains("test1");
    }

    @Test
    @DisplayName("기본 매칭 룰은 경로가 없어도 이벤트를 반환한다.")
    void matchTest2(){
        TestRuleImpl1 rule = new TestRuleImpl1("test", "target");

        testMatcher.add(rule);

        testMatcher.run(getInfo(null));

        List<TestEvent> events = getEvents();
        assertThat(events).extracting(TestEvent::getEventName)
                .contains("target");
    }

    @Test
    @DisplayName("기본 룰은 패턴이 없어도 이벤트를 반환한다.")
    void matchTest3(){
        TestRuleImpl1 rule = new TestRuleImpl1(null, "target");

        testMatcher.add(rule);

        testMatcher.run(getInfo("destination"));

        List<TestEvent> events = getEvents();
        assertThat(events).extracting(TestEvent::getEventName)
                .contains("target");
    }

    @Test
    @DisplayName("커스텀 매칭 로직도 작동한다. - 세션 구독 id 매칭")
    void matchTest4(){
        TestRuleImpl2 rule = new TestRuleImpl2("test-sub", "target");
        TestRuleImpl2 rule2 = new TestRuleImpl2("test-sub2", "nontarget");

        testMatcher.add(rule);
        testMatcher.add(rule2);

        testMatcher.run(getInfo("asdfhjkl"));

        List<TestEvent> events = getEvents();
        assertThat(events).extracting(TestEvent::getEventName)
                .contains("target");
    }


}
