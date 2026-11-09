package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * 업무 이벤트를 공통 알림 요청으로 변환하는 규칙과 재발행 실패 처리를 검증한다.
 * 발행자는 Mock으로 대체하여 수신자·문구·수준, 빈 이름 대체, 실패 후 처리와 로그를 확인한다.
 *
 * <p>리스너 메서드를 직접 호출하므로 Spring의 AFTER_COMMIT 실행 시점은 검증하지 않는다.
 * 트랜잭션 경계와 실제 팔로우부터 알림 저장까지의 연동은 NotificationIntegrationTest가 담당한다.
 */
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

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "USER, ADMIN, 회원님의 권한이 USER에서 ADMIN으로 변경되었습니다.",
            "ADMIN, USER, 회원님의 권한이 ADMIN에서 USER로 변경되었습니다."
    })
    @DisplayName("권한 변경 이벤트를 변경 대상 사용자의 INFO 알림 요청으로 한 번 발행한다.")
    void publishesNotificationRequestForRoleChangedUser(Role previousRole, Role newRole, String expectedContent){
        UUID userId = UUID.randomUUID();
        UserRoleChangedEvent event = new UserRoleChangedEvent(userId, previousRole, newRole);

        listener.handleUserRoleChanged(event);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        NotificationCreateRequestedEvent request = eventCaptor.getValue();
        assertThat(request.receiverId()).isEqualTo(userId);
        assertThat(request.title()).isEqualTo("권한 변경");
        assertThat(request.content()).isEqualTo(expectedContent);
        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
    }

    @Test
    @DisplayName("구독 이벤트의 이름과 제목으로 소유자에게 INFO 알림 요청을 한 번 발행한다.")
    void publishesNotificationRequestForPlaylistOwner(){
        UUID playListId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriberId = UUID.randomUUID();

        PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(playListId, "퇴근 후 영화", ownerId, subscriberId, "홍길동");

        listener.handlePlaylistSubscribed(event);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationCreateRequestedEvent request = eventCaptor.getValue();

        assertThat(request.receiverId()).isEqualTo(ownerId);
        assertThat(request.title()).isEqualTo("새로운 구독자");
        assertThat(request.content())
                .isEqualTo(
                        "홍길동님이 \"퇴근 후 영화\" 플레이리스트를 구독했습니다.");

        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("구독자 이름이 없거나 공백이면 사용자라는 이름으로 알림을 생성한다.")
    void usesDefaultSubscriberNameWhenMissingOrBlank(String subscriberName){
        PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(UUID.randomUUID(), "퇴근 후 영화", UUID.randomUUID(), UUID.randomUUID(), subscriberName);
        listener.handlePlaylistSubscribed(event);

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().content())
                .isEqualTo("사용자님이 \"퇴근 후 영화\" 플레이리스트를 구독했습니다.");
    }

    @Test
    @DisplayName("플레이리스트 소유자와 구독자가 같으면 알림 요청을 발행하지 않는다.")
    void doesNotPublishNotificationForOwnPlaylistSubscription(){
        UUID userId = UUID.randomUUID();
        PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(UUID.randomUUID(), "퇴근 후 영화", userId, userId, "홍길동");
        listener.handlePlaylistSubscribed(event);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("권한 변경 알림 요청 발행이 실패해도 예외를 전파하지 않고 다음 이벤트를 처리한다.")
    void publishesNextRoleChangeNotificationRequestAfterFailure(){
        UserRoleChangedEvent failedEvent = new UserRoleChangedEvent(UUID.randomUUID(), Role.USER, Role.ADMIN);
        UserRoleChangedEvent nextEvent = new UserRoleChangedEvent(UUID.randomUUID(), Role.ADMIN, Role.USER);

        doThrow(new IllegalStateException("테스트용 권한 변경 알림 발행 실패"))
                .doNothing()
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() -> {
            listener.handleUserRoleChanged(failedEvent);
            listener.handleUserRoleChanged(nextEvent);
        }).doesNotThrowAnyException();

        verify(eventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactly(failedEvent.userId(), nextEvent.userId());
        assertThat(eventCaptor.getAllValues().get(1).content())
                .isEqualTo("회원님의 권한이 ADMIN에서 USER로 변경되었습니다.");

    }

    @Test
    @DisplayName("구독 알림 요청 발행이 실패해도 예외를 전파하지 않고 다음 이벤트를 처리한다.")
    void publishesNextSubscriptionNotificationRequestAfterFailure(){
        PlaylistSubscribedEvent failedEvent = new PlaylistSubscribedEvent(UUID.randomUUID(), "퇴근 후 영화", UUID.randomUUID(), UUID.randomUUID(), "첫 구독자");
        PlaylistSubscribedEvent nextEvent = new PlaylistSubscribedEvent(UUID.randomUUID(), "주말 영화", UUID.randomUUID(), UUID.randomUUID(), "다음 구독자");

        doThrow(new IllegalStateException("테스트용 구독 알림 발행 실패"))
                .doNothing()
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() -> {
            listener.handlePlaylistSubscribed(failedEvent);
            listener.handlePlaylistSubscribed(nextEvent);
        }).doesNotThrowAnyException();

        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactly(failedEvent.ownerId(), nextEvent.ownerId());

        assertThat(eventCaptor.getAllValues().get(1).content())
                .isEqualTo("다음 구독자님이 \"주말 영화\" 플레이리스트를 구독했습니다.");
    }


    private UserFollowedEvent createUserFollowedEvent(String followerName) {
        return new UserFollowedEvent(UUID.randomUUID(), followerName, UUID.randomUUID());
    }

}
