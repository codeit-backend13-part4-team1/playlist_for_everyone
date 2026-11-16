package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 팔로우·권한 변경과 플레이리스트 구독·생성·콘텐츠 추가 이벤트의 알림 변환 규칙을 검증한다.
 * 발행자와 수신자 조회 컴포넌트를 Mock으로 대체하여 수신자·문구·수준과 이름 보정 규칙을 확인한다.
 *
 * <p>플레이리스트 생성·콘텐츠 추가에서는 조회 결과의 중복과 행위자를 제외하는지,
 * 조회 실패 시 발행을 중단하고 개별 발행 실패 시 나머지 수신자를 처리하는지 확인한다.
 * 실패 로그의 업무 식별자·예외 타입과 조회 실패 로그의 이름·제목·원본 메시지 제외도 검증한다.
 *
 * <p>리스너 메서드를 직접 호출하므로 Spring의 AFTER_COMMIT 실행 시점은 검증하지 않는다.
 * 실제 업무 커밋부터 알림 저장·SSE 발행 요청까지의 연동은 NotificationIntegrationTest와
 * NotificationPlaylistIntegrationTest가 담당한다.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("업무 이벤트 기반 알림 변환 리스너")
class NotificationDomainEventListenerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NotificationTargetReader targetReader;

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
        // 구독 서비스의 자기 구독 거부와 별개로, 소비자가 같은 사용자에게 알림을 만들지 않는 규칙을 확인한다.
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

    @Test
    @DisplayName("플레이리스트 생성 시 소유자의 각 팔로워에게 이벤트의 이름과 제목으로 INFO 알림 생성을 요청한다.")
    void publishesPlaylistCreatedNotificationRequestsToFollowers(){
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(playlistId, "퇴근 후 영화", ownerId, "홍길동");
        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);
        assertThat(eventCaptor.getAllValues()).allSatisfy(request ->{
            assertThat(request.title())
                    .isEqualTo("팔로우한 사용자의 새 플레이리스트");
            assertThat(request.content())
                    .isEqualTo("홍길동님이 \"퇴근 후 영화\" 플레이리스트를 만들었습니다.");
            assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
        });
    }

    @Test
    @DisplayName("플레이리스트 생성 시 소유자의 팔로워가 없으면 알림 생성 요청을 발행하지 않는다.")
    void doesNotPublishPlaylistCreatedNotificationWhenNoFollowers(){
        UUID ownerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");

        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of());

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("플레이리스트 생성 시 팔로워 ID가 중복되어도 각 사용자에게 알림 생성 요청을 한 번만 발행한다.")
    void publishesPlaylistCreatedNotificationOncePerDistinctFollower(){
        UUID ownerId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");
        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId, firstFollowerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(eventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);

    }

    @Test
    @DisplayName("플레이리스트 생성 시 조회된 팔로워에 소유자가 포함되어도 소유자를 제외한 사용자에게만 알림을 요청한다.")
    void excludesOwnerFromPlaylistCreatedNotificationRecipients(){
        UUID ownerId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");

        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(ownerId, followerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue().receiverId())
                .isEqualTo(followerId);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("플레이리스트 소유자의 이름이 없거나 공백이면 사용자라는 이름으로 알림을 요청한다.")
    void useDefaultOwnerNameWhenMissingOrBlank(String ownerName){
        UUID ownerId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, ownerName);
        when(targetReader.findFollowerIds(ownerId)).thenReturn(List.of(followerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().content())
                .isEqualTo("사용자님이 \"퇴근 후 영화\" 플레이리스트를 만들었습니다.");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("플레이리스트 생성 알림의 팔로워 조회가 실패하면 예외를 전파하지 않고 식별자와 예외 타입을 기록한다.")
    void handlesAndLogsFollowerLookupFailure(CapturedOutput output){
        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "로그에 노출하지 않을 제목", UUID.randomUUID(), "로그에 노출하지 않을 이름");
        String failureMessage = "테스트용 팔로워 조회실패:" +event.ownerName() + "/" + event.playlistTitle();

        when(targetReader.findFollowerIds(event.ownerId()))
                .thenThrow(new IllegalStateException(failureMessage));

        assertThatCode(() -> listener.handlePlaylistCreated(event))
                .doesNotThrowAnyException();

        verify(targetReader).findFollowerIds(event.ownerId());
        verifyNoInteractions(eventPublisher);

        assertThat(output.getAll())
                .contains("playlistId="+ event.playlistId(),
                        "ownerId="+event.ownerId(),
                        "exceptionType=IllegalStateException")
                .doesNotContain(
                        event.ownerName(),
                        event.playlistTitle(),
                        failureMessage
                );
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("플레이리스트 생성 알림 요청의 첫 발행이 실패해도 실패를 기록하고 나머지 수신자에게 발행한다.")
    void continuesPlaylistCreatedNotificationsAfterPublishFailure(CapturedOutput output){
        UUID ownerId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");
        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId));

        doThrow(new IllegalStateException("테스트용 알림 요청 발행 실패"))
                .doNothing()
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() -> listener.handlePlaylistCreated(event))
                .doesNotThrowAnyException();

        verify(targetReader).findFollowerIds(ownerId);
        verify(eventPublisher, times(2))
                .publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);

        UUID failedReceiverId = eventCaptor.getAllValues().get(0).receiverId();
        assertThat(output.getAll())
                .contains("playlistId=" + event.playlistId(),
                        "ownerId=" + ownerId,
                        "receiverId=" + failedReceiverId,
                        "exceptionType=IllegalStateException");
    }

    @Test
    @DisplayName("콘텐츠 추가 시 각 구독자에게 이벤트의 플레이리스트 제목과 콘텐츠 제목으로 INFO 알림을 요청한다.")
    void publishesPlaylistContentAddedNotificationRequestsToSubscribers(){
        UUID playlistId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", actorId);
        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(firstSubscriberId, secondSubscriberId));

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstSubscriberId, secondSubscriberId);
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(request ->{
                    assertThat(request.title())
                            .isEqualTo("구독한 플레이리스트에 새 콘텐츠");
                    assertThat(request.content())
                            .isEqualTo("\"퇴근 후 영화\" 플레이리스트에 \"인셉션\" 콘텐츠가 추가되었습니다.");
                    assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
                });
    }

    @Test
    @DisplayName("콘텐츠 추가 시 플레이리스트의 구독자가 없으면 알림 생성 요청을 발행하지 않는다.")
    void doesNotPublishPlaylistContentAddedNotificationWhenNoSubscribers(){
        UUID playlistId = UUID.randomUUID();
        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", UUID.randomUUID());

        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of());

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verifyNoInteractions(eventPublisher);

    }

    @Test
    @DisplayName("콘텐츠 추가 시 구독자 ID가 중복되어도 각 사용자에게 알림 생성 요청을 한 번만 발행한다.")
    void publishesPlaylistContentAddedNotificationOncePerDistinctSubscriber(){
        UUID playlistId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", UUID.randomUUID());
        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(firstSubscriberId, secondSubscriberId, firstSubscriberId));

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstSubscriberId, secondSubscriberId);
    }

    @Test
    @DisplayName("콘텐츠 추가 시 조회된 구독자에 추가한 사용자가 포함되어도 해당 사용자를 제외하고 알림을 요청한다.")
    void excludesActorFromPlaylistContentAddedNotificationRecipients(){
        UUID playlistId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID subscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", actorId);
        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(actorId,subscriberId));

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().receiverId()).isEqualTo(subscriberId);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("콘텐츠 추가 알림의 구독자 조회가 실패하면 예외를 전파하지 않고 식별자와 예외 타입을 기록한다.")
    void handlesAndLogsSubscriberLookupFailure(CapturedOutput output){
        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(UUID.randomUUID(),
                "로그에 노출하지 않을 플레이리스트 제목", UUID.randomUUID(),
                "로그에 노출하지 않을 콘텐츠 제목", UUID.randomUUID());

        String failureMessage = "테스트용 구독자 조회실패:" + event.playlistTitle() + "/" + event.contentTitle();

        when(targetReader.findSubscriberIds(event.playlistId()))
                .thenThrow(new IllegalStateException(failureMessage));

        assertThatCode(() -> listener.handlePlaylistContentAdded(event))
                .doesNotThrowAnyException();

        verify(targetReader).findSubscriberIds(event.playlistId());
        verifyNoInteractions(eventPublisher);

        assertThat(output.getAll())
                .contains(
                        "playlistId=" + event.playlistId(),
                        "contentId=" + event.contentId(),
                        "actorId=" + event.actorId(),
                        "exceptionType=IllegalStateException"
                ).doesNotContain(
                        event.playlistTitle(),
                        event.contentTitle(),
                        failureMessage
                );
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("콘텐츠 추가 알림 요청의 첫 발행이 실패해도 실패를 기록하고 나머지 구독자에게 발행한다.")
    void continuesPlaylistContentAddedNotificationsAfterPublishFailure(CapturedOutput output){
        UUID playlistId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", UUID.randomUUID());

        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(firstSubscriberId, secondSubscriberId));

        doThrow(new IllegalStateException("테스트용 알림 요청 발행 실패"))
                .doNothing()
                .when(eventPublisher)
                .publishEvent(any(NotificationCreateRequestedEvent.class));

        assertThatCode(() -> listener.handlePlaylistContentAdded(event))
                .doesNotThrowAnyException();

        verify(targetReader).findSubscriberIds(playlistId);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstSubscriberId, secondSubscriberId);

        UUID failedReceiverId = eventCaptor.getAllValues().get(0).receiverId();
        assertThat(output.getAll())
                .contains("playlistId=" + event.playlistId(),
                        "contentId=" + event.contentId(),
                        "actorId=" + event.actorId(),
                        "receiverId=" + failedReceiverId,
                        "exceptionType=IllegalStateException");
    }


    private UserFollowedEvent createUserFollowedEvent(String followerName) {
        return new UserFollowedEvent(UUID.randomUUID(), followerName, UUID.randomUUID());
    }

}
