package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("업무 이벤트 기반 알림 변환 리스너")
class NotificationDomainEventListenerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationDomainEventListener listener;

    @Captor
    private ArgumentCaptor<NotificationCreateRequestedEvent> eventCaptor;

    @Test
    @DisplayName("팔로우 이벤트를 팔로우받은 사용자의 알림 생성 요청으로 한 번 발행한다.")
    void publishesNotificationRequestForFollowee(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        UserFollowedEvent event = new UserFollowedEvent(followerId, "홍길동", followeeId);
        listener.handleUserFollowed(event);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationCreateRequestedEvent request = eventCaptor.getValue();

        assertThat(request.receiverId()).isEqualTo(followeeId);
        assertThat(request.title()).isEqualTo("새로운 팔로워");
        assertThat(request.content())
                .isEqualTo("홍길동님이 회원님을 팔로우했습니다.");
        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);

    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("팔로워 이름이 없거나 공백이면 사용자라는 이름으로 알림을 생성한다.")
    void usesDefaultFollowerNameWhenMissingOrBlank(String followerName){
        UserFollowedEvent event = createUserFollowedEvent(followerName);

        listener.handleUserFollowed(event);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().content()).isEqualTo("사용자님이 회원님을 팔로우했습니다.");
    }

    @Test
    @DisplayName("알림 요청 발행이 실패해도 다음 이벤트의 알림 요청을 정상 발행한다.")
    void publishesNextNotificationRequestAfterFailure(){
        UserFollowedEvent failedEvent = createUserFollowedEvent("첫 사용자");
        UserFollowedEvent nextEvent = createUserFollowedEvent("다음 사용자");

        doThrow(new IllegalStateException("테스트용 알림 요청 발행 실패"))
                .doNothing()
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() ->{
            listener.handleUserFollowed(failedEvent);
            listener.handleUserFollowed(nextEvent);
        }).doesNotThrowAnyException();

        verify(eventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactly(failedEvent.followeeId(), nextEvent.followeeId());
        assertThat(eventCaptor.getAllValues().get(1).content())
                .isEqualTo("다음 사용자님이 회원님을 팔로우했습니다.");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("알림 요청 발행 실패 시 예외를 전파 하지 않고 식별자와 예외 타입을 기록한다.")
    void handlesAndLogsNotificationRequestPublishFailure(CapturedOutput output){
        UserFollowedEvent event = createUserFollowedEvent("로그에 노출하지 않을 이름");

        String failureMessage = "테스트용 발행 실패:" + event.followerName();

        doThrow(new IllegalStateException(failureMessage))
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() -> listener.handleUserFollowed(event))
                .doesNotThrowAnyException();

        verify(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThat(output.getAll())
                .contains(
                        "팔로우 알림 변환·재발행 실패.",
                        "followerId=" + event.followerId(),
                        "followeeId=" + event.followeeId(),
                        "exceptionType=IllegalStateException"
                ).doesNotContain(event.followerName(),failureMessage);
    }

    private UserFollowedEvent createUserFollowedEvent(String followerName) {
        return new UserFollowedEvent(UUID.randomUUID(), followerName, UUID.randomUUID());
    }

}