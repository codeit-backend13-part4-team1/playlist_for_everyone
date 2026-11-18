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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 웹 소켓의 경로에 따라 채밍 및 이벤트를 발생하는 로직을 테스트한다.
 * - 여러개의 DestinationRule 상속 구현체를 저장 할 수 있는지.
 * - Rule 의 정규표현식으로 주입될 SessionInfo 객체를 정상적으로 매치할 수 있는지
 * - 매칭된 Rule 의 실행 로직을 잘 실행할 수 있는지 (이벤트를 발생 시키는지)
 * 테스트 한다.
 * 실제 이벤트의 종류는 Spring 자체 이벤트 리스너로 책임하므로, 이벤트는 임의 이벤트를 설정하여 테스트 한다.
 */

@ExtendWith(MockitoExtension.class)
@DisplayName("웹 소켓 경로 매쳐 테스트")
public class DestinationMatcherTest{

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
    static class TestRuleImpl1 extends DestinationRule<TestEvent1>{
        String eventName;
        public TestRuleImpl1(Pattern pattern, String name){
            super(pattern);
            eventName = name;
        }

        public TestRuleImpl1(Pattern pattern){
            super(pattern);
            eventName = "test1";
        }

        @Override
        public TestEvent1 run(SessionInfo sessionInfo){
            return new TestEvent1(eventName);
        }
    }

    static class TestRuleImpl2 extends DestinationRule<TestEvent2>{
        public TestRuleImpl2(Pattern pattern){
            super(pattern);
        }

        @Override
        public TestEvent2 run(SessionInfo sessionInfo){
            return new TestEvent2("test2");
        }
    }

    @Mock
    ApplicationEventPublisher publisher;

    DestinationMatcher testMatcher;


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
        testMatcher = new DestinationMatcher(publisher);
    }

    @Test
    @DisplayName("DestinationRule 상속 구현체를 여러 종류 저장 하고 실행 할 수 있다.")
    void saveMultipleDestinationRule(){
        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(Pattern.compile("^/test.*$"));
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test.*$"),"test1 - 1");
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(Pattern.compile("^/test.*$"));

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);


        testMatcher.run(getInfo("/test/123"));

        List<TestEvent> events = getEvents();
        assertThat(events)
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder(
                        "test1",
                        "test1 - 1",
                        "test2"
                );
    }

    @Test
    @DisplayName("source 가 없다면 Rule 이 매칭되지 않는다.")
    void runWithNoSource(){
        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(Pattern.compile("^/test.*$"));
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test.*$"));
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(Pattern.compile("^/test.*$"));

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);

        testMatcher.run(null);

        List<TestEvent> events = getEvents();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("SessionInfo 에 destination 이 없다면, Rule 이 매칭되지 않는다.")
    void runWithNoDestination(){
        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(Pattern.compile("^/test.*$"));
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test.*$"));
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(Pattern.compile("^/test.*$"));

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);

        testMatcher.run(getInfo(null));

        List<TestEvent> events = getEvents();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("특정 경로와 매칭되는 Rule 이 있다면, 해당 이벤트만을 발생 시킬 수 있다.")
    void runWithMatchingRules(){
        Pattern pattern = Pattern.compile("^/test1/1.*$");

        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(pattern,"target");
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test1/2$"));
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(Pattern.compile("^/test2/3.[0-9]$"));

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);

        testMatcher.run(getInfo("/test1/123"));

        List<TestEvent> events = getEvents();

        assertThat(events)
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("target");
    }

    @Test
    @DisplayName("특정 경로와 매칭되는 Rule 이 여러개라면, 해당 이벤트들을 발생시킨다.")
    void runWithMultipleMatchingRules(){
        Pattern pattern = Pattern.compile("^/test1/1.*$");

        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(pattern,"target1");
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test1/2$"),"target2");
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(pattern);

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);

        testMatcher.run(getInfo("/test1/123"));

        List<TestEvent> events = getEvents();

        assertThat(events)
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder(
                        "target1",
                        "test2");
    }

    @Test
    @DisplayName("매칭된 Rule 이 없다면, 어떠한 이벤트도 실행하지 않는다.")
    void runWithNoMatchingRule(){
        TestRuleImpl1 testRuleImpl1 = new TestRuleImpl1(Pattern.compile("^/test1/1.*$"));
        TestRuleImpl1 testRuleImpl2 = new TestRuleImpl1(Pattern.compile("^/test1/2$"));
        TestRuleImpl2 testRuleImpl3 = new TestRuleImpl2(Pattern.compile("^/test2/3.[0-9]$"));

        testMatcher.add(testRuleImpl1);
        testMatcher.add(testRuleImpl2);
        testMatcher.add(testRuleImpl3);

        testMatcher.run(getInfo("/test3/123"));

        List<TestEvent> events = getEvents();
        assertThat(events).isEmpty();
    }



}
