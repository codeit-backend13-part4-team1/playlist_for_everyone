package com.codeit.sb13.playlist_for_everyone.domain.notification.event.listener;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.event.DirectMessageCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.follow.event.UserFollowedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationCreateRequestedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationPublishBatch;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationSource;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationAsyncProcessor;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationSubmitter;
import com.codeit.sb13.playlist_for_everyone.domain.notification.monitoring.NotificationMetrics;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl.NotificationDeduplicationStore;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl.NotificationDeduplicationStore.Reservation;
import com.codeit.sb13.playlist_for_everyone.domain.notification.service.NotificationTargetReader;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistContentAddedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.event.PlaylistSubscribedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.event.WatchingSessionStartedEvent;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * 업무 이벤트별 수신자·문구·수준과 중복·자기 알림 제외 규칙을 검증한다.
 * 프로세서는 Mock으로 분리하고 한 업무 이벤트에서 완성한 요청 목록·업무 식별자를 확인한다.
 * 조회 실패는 이 리스너가 처리하며, 개별 재발행 실패는 NotificationAsyncProcessorTest에서 검증한다.
 * 실제 비동기 실행·제출 거부·업무 커밋은 별도 통합 테스트가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("업무 이벤트 기반 알림 변환 리스너")
class NotificationDomainEventListenerTest {

    @Mock
    private NotificationAsyncProcessor processor;

    @Mock
    private NotificationTargetReader targetReader;

    @Mock
    private NotificationMetrics metrics;

    @Mock
    private NotificationDeduplicationStore store;

    private NotificationDomainEventListener listener;

    @Captor
    private ArgumentCaptor<NotificationPublishBatch> batchCaptor;

    @BeforeEach
    void setUp(){
        NotificationSubmitter notificationSubmitter = new NotificationSubmitter(processor, store);
        listener = new NotificationDomainEventListener(
                targetReader,
                processor,
                metrics,
                notificationSubmitter
        );

        lenient().when(store.tryReserve(anyString()))
                .thenReturn(Optional.of(new Reservation("test-key", "test-token")));
    }

    @Test
    @DisplayName("팔로우 이벤트를 팔로우받은 사용자의 알림 생성 요청 목록으로 한 번 제출한다.")
    void submitsNotificationRequestForFollowee(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        UserFollowedEvent event = new UserFollowedEvent(followerId, "홍길동", followeeId);
        listener.handleUserFollowed(event);

        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("팔로우", Map.of("followerId", followerId, "followeeId", followeeId), NotificationSource.USER_FOLLOWED);
        NotificationCreateRequestedEvent request = batchCaptor.getValue().requests().get(0);

        assertThat(request.receiverId()).isEqualTo(followeeId);
        assertThat(request.title()).isEqualTo("새로운 팔로워");
        assertThat(request.content())
                .isEqualTo("홍길동님이 회원님을 팔로우했습니다.");
        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);

    }

    @Test
    @DisplayName("제한 시간 안에 같은 상대를 다시 팔로우하면 알림 작업을 한 번만 제출한다.")
    void skipsRepeatedFollowNotification(){
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        UserFollowedEvent event = new UserFollowedEvent(followerId, "홍길동", followeeId);
        String actionKey = "follow:%s:%s".formatted(followerId, followeeId);
        when(store.tryReserve(actionKey))
                .thenReturn(Optional.of(new Reservation(actionKey, "test-token")))
                .thenReturn(Optional.empty());

        listener.handleUserFollowed(event);
        listener.handleUserFollowed(event);

        verify(processor, times(1)).publish(any(NotificationPublishBatch.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("팔로워 이름이 없거나 공백이면 사용자라는 이름으로 알림을 생성한다.")
    void usesDefaultFollowerNameWhenMissingOrBlank(String followerName){
        UserFollowedEvent event = createUserFollowedEvent(followerName);

        listener.handleUserFollowed(event);

        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests().get(0).content()).isEqualTo("사용자님이 회원님을 팔로우했습니다.");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "USER, ADMIN, 회원님의 권한이 USER에서 ADMIN으로 변경되었습니다.",
            "ADMIN, USER, 회원님의 권한이 ADMIN에서 USER로 변경되었습니다."
    })
    @DisplayName("권한 변경 이벤트를 변경 대상 사용자의 INFO 알림 요청으로 한 번 제출한다.")
    void submitsNotificationRequestForRoleChangedUser(Role previousRole, Role newRole, String expectedContent){
        UUID userId = UUID.randomUUID();
        UserRoleChangedEvent event = new UserRoleChangedEvent(userId, previousRole, newRole);

        listener.handleUserRoleChanged(event);

        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("권한 변경", Map.of("userId", userId), NotificationSource.USER_ROLE_CHANGED);

        NotificationCreateRequestedEvent request = batchCaptor.getValue().requests().get(0);
        assertThat(request.receiverId()).isEqualTo(userId);
        assertThat(request.title()).isEqualTo("권한 변경");
        assertThat(request.content()).isEqualTo(expectedContent);
        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
    }

    @Test
    @DisplayName("구독 이벤트의 이름과 제목으로 소유자에게 INFO 알림 요청을 한 번 제출한다.")
    void submitsNotificationRequestForPlaylistOwner(){
        UUID playListId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID subscriberId = UUID.randomUUID();

        PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(playListId, "퇴근 후 영화", ownerId, subscriberId, "홍길동");

        listener.handlePlaylistSubscribed(event);

        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("플레이리스트 구독", Map.of("playlistId", playListId, "ownerId", ownerId, "subscriberId", subscriberId),
                NotificationSource.PLAYLIST_SUBSCRIBED);
        NotificationCreateRequestedEvent request = batchCaptor.getValue().requests().get(0);

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

        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests().get(0).content())
                .isEqualTo("사용자님이 \"퇴근 후 영화\" 플레이리스트를 구독했습니다.");
    }

    @Test
    @DisplayName("플레이리스트 소유자와 구독자가 같으면 알림 요청을 제출하지 않는다.")
    void doesNotSubmitNotificationForOwnPlaylistSubscription(){
        // 구독 서비스의 자기 구독 거부와 별개로, 소비자가 같은 사용자에게 알림을 만들지 않는 규칙을 확인한다.
        UUID userId = UUID.randomUUID();
        PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(UUID.randomUUID(), "퇴근 후 영화", userId, userId, "홍길동");
        listener.handlePlaylistSubscribed(event);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("플레이리스트 생성 시 소유자의 각 팔로워에게 이벤트의 이름과 제목으로 INFO 알림 생성을 요청한다.")
    void submitsPlaylistCreatedNotificationRequestsToFollowers(){
        UUID playlistId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(playlistId, "퇴근 후 영화", ownerId, "홍길동");
        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("플레이리스트 생성", Map.of("playlistId", playlistId, "ownerId", ownerId), NotificationSource.PLAYLIST_CREATED);

        assertThat(batchCaptor.getValue().requests())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);
        assertThat(batchCaptor.getValue().requests()).allSatisfy(request ->{
            assertThat(request.title())
                    .isEqualTo("팔로우한 사용자의 새 플레이리스트");
            assertThat(request.content())
                    .isEqualTo("홍길동님이 \"퇴근 후 영화\" 플레이리스트를 만들었습니다.");
            assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
        });
    }

    @Test
    @DisplayName("플레이리스트 생성 시 소유자의 팔로워가 없으면 알림 생성 요청을 제출하지 않는다.")
    void doesNotSubmitPlaylistCreatedNotificationWhenNoFollowers(){
        UUID ownerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");

        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of());

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("플레이리스트 생성 시 팔로워 ID가 중복되어도 각 사용자에게 알림 생성 요청을 한 번만 발행한다.")
    void submitsPlaylistCreatedNotificationOncePerDistinctFollower(){
        UUID ownerId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        PlaylistCreatedEvent event = new PlaylistCreatedEvent(UUID.randomUUID(), "퇴근 후 영화", ownerId, "홍길동");
        when(targetReader.findFollowerIds(ownerId))
                .thenReturn(List.of(firstFollowerId, secondFollowerId, firstFollowerId));

        listener.handlePlaylistCreated(event);

        verify(targetReader).findFollowerIds(ownerId);
        verify(processor)
                .publish(batchCaptor.capture());

        assertThat(batchCaptor.getValue().requests())
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
        verify(processor).publish(batchCaptor.capture());

        assertThat(batchCaptor.getValue().requests())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactly(followerId);
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
        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests().get(0).content())
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
        verify(metrics).recordLookupFailure();
        verifyNoInteractions(processor);

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
    @DisplayName("콘텐츠 추가 시 각 구독자에게 이벤트의 플레이리스트 제목과 콘텐츠 제목으로 INFO 알림을 요청한다.")
    void submitsPlaylistContentAddedNotificationRequestsToSubscribers(){
        UUID playlistId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", actorId);
        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(firstSubscriberId, secondSubscriberId));

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("콘텐츠 추가", Map.of("playlistId", playlistId, "contentId", event.contentId(), "actorId", actorId),
                NotificationSource.PLAYLIST_CONTENT_ADDED);
        assertThat(batchCaptor.getValue().requests())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstSubscriberId, secondSubscriberId);
        assertThat(batchCaptor.getValue().requests())
                .allSatisfy(request ->{
                    assertThat(request.title())
                            .isEqualTo("구독한 플레이리스트에 새 콘텐츠");
                    assertThat(request.content())
                            .isEqualTo("\"퇴근 후 영화\" 플레이리스트에 \"인셉션\" 콘텐츠가 추가되었습니다.");
                    assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
                });
    }

    @Test
    @DisplayName("콘텐츠 추가 시 플레이리스트의 구독자가 없으면 알림 생성 요청을 제출하지 않는다.")
    void doesNotSubmitPlaylistContentAddedNotificationWhenNoSubscribers(){
        UUID playlistId = UUID.randomUUID();
        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", UUID.randomUUID());

        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of());

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verifyNoInteractions(processor);

    }

    @Test
    @DisplayName("콘텐츠 추가 시 구독자 ID가 중복되어도 각 사용자에게 알림 생성 요청을 한 번만 발행한다.")
    void submitsPlaylistContentAddedNotificationOncePerDistinctSubscriber(){
        UUID playlistId = UUID.randomUUID();
        UUID firstSubscriberId = UUID.randomUUID();
        UUID secondSubscriberId = UUID.randomUUID();

        PlaylistContentAddedEvent event = new PlaylistContentAddedEvent(playlistId, "퇴근 후 영화", UUID.randomUUID(), "인셉션", UUID.randomUUID());
        when(targetReader.findSubscriberIds(playlistId))
                .thenReturn(List.of(firstSubscriberId, secondSubscriberId, firstSubscriberId));

        listener.handlePlaylistContentAdded(event);

        verify(targetReader).findSubscriberIds(playlistId);
        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests())
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
        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests())
                .extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactly(subscriberId);
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
        verify(metrics).recordLookupFailure();
        verifyNoInteractions(processor);

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
    @DisplayName("DM 생성 이벤트를 수신자의 INFO 알림 생성 요청으로 변환하여 한 번 제출한다.")
    void submitsNotificationRequestForDirectMessageReceiver(){
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), senderId, "홍길동", receiverId);

        listener.handleDirectMessageCreated(event);
        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("DM", Map.of("directMessageId", event.directMessageId(), "conversationId", event.conversationId(), "receiverId", receiverId),
                NotificationSource.DIRECT_MESSAGE_CREATED);
        NotificationCreateRequestedEvent request = batchCaptor.getValue().requests().get(0);

        assertThat(request.receiverId()).isEqualTo(receiverId);
        assertThat(request.title()).isEqualTo("새로운 메시지");
        assertThat(request.content()).isEqualTo("홍길동님이 메시지를 보냈습니다.");
        assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("DM 발신자 이름이 없거나 공백이면 사용자라는 이름으로 알림을 요청한다.")
    void usesDefaultDirectMessageSenderNameWhenMissingOrBlank(String senderName){
        DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                senderName,
                UUID.randomUUID()
        );

        listener.handleDirectMessageCreated(event);
        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests().get(0).content()).isEqualTo("사용자님이 메시지를 보냈습니다.");
    }

    @Test
    @DisplayName("DM 발신자와 수신자가 같으면 알림 생성 요청을 제출하지 않는다.")
    void doesNotSubmitDirectMessageNotificationForSender(){
        UUID userId = UUID.randomUUID();

        DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                "홍길동",
                userId
        );

        listener.handleDirectMessageCreated(event);
        verifyNoInteractions(processor);
    }


    @Test
    @DisplayName("시청 시작 시 본인을 제외한 팔로워별 알림 요청을 한 번 제출한다.")
    void submitsWatchingSessionNotificationsToDistinctFollowers(){
        UUID watcherId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();

        when(targetReader.findFollowerIds(watcherId))
                .thenReturn(List.of(firstFollowerId, watcherId, firstFollowerId, secondFollowerId));

        listener.handleWatchingSessionStarted(new WatchingSessionStartedEvent(
                watcherId, "홍길동", contentId, "인터스텔라"));

        verify(processor).publish(batchCaptor.capture());
        assertBatchSource("콘텐츠 시청 시작",
                Map.of("contentId", contentId,"watcherId", watcherId),
                NotificationSource.WATCHING_SESSION_STARTED);
        List<NotificationCreateRequestedEvent> requests = batchCaptor.getValue().requests();
        assertThat(requests).extracting(NotificationCreateRequestedEvent::receiverId)
                .containsExactlyInAnyOrder(firstFollowerId, secondFollowerId);
        assertThat(requests).allSatisfy(request ->{
            assertThat(request.title()).isEqualTo("팔로우한 사용자의 같이 보기 참여");
            assertThat(request.content()).isEqualTo("홍길동님이 \"인터스텔라\" 같이 보기에 참여했습니다.");
            assertThat(request.level()).isEqualTo(NotificationLevel.INFO);
        });

    }

    @Test
    @DisplayName("팔로워 조회 결과에 시청자 본인만 있으면 알림 작업을 제출하지 않는다.")
    void doesNotSubmitWatchingSessionNotificationWhenOnlyWatcherRemains(){
        UUID watcherId = UUID.randomUUID();
        when(targetReader.findFollowerIds(watcherId)).thenReturn(List.of(watcherId));
        listener.handleWatchingSessionStarted(new WatchingSessionStartedEvent(
                watcherId, "홍길동", UUID.randomUUID(), "인터스텔라"));
        verify(targetReader).findFollowerIds(watcherId);
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("시청자 이름이 공백이면 같이 보기 참여 알림에서 사용자로 표시한다.")
    void usesDefaultWatcherNameWhenBlank(){
        UUID watcherId = UUID.randomUUID();
        when(targetReader.findFollowerIds(watcherId)).thenReturn(List.of(UUID.randomUUID()));
        listener.handleWatchingSessionStarted(new WatchingSessionStartedEvent(
                watcherId, " ", UUID.randomUUID(), "인터스텔라"));

        verify(processor).publish(batchCaptor.capture());
        assertThat(batchCaptor.getValue().requests())
                .extracting(NotificationCreateRequestedEvent::content)
                .containsExactly("사용자님이 \"인터스텔라\" 같이 보기에 참여했습니다.");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("시청 알림의 팔로워 조회 실패를 기록하고 작업을 제출하지 않는다.")
    void handlesAndLogsWatchingSessionFollowerLookupFailure(CapturedOutput output){
        UUID watcherId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        WatchingSessionStartedEvent event = new WatchingSessionStartedEvent(watcherId, "홍길동", contentId, "인터스텔라");
        String failureMessage = "테스트용 팔로워 조회 실패";

        when(targetReader.findFollowerIds(watcherId))
                .thenThrow(new IllegalStateException(failureMessage));
        assertThatCode(() -> listener.handleWatchingSessionStarted(event))
                .doesNotThrowAnyException();

        verify(metrics).recordLookupFailure();
        verifyNoInteractions(processor);
        assertThat(output.getAll())
                .contains("contentId=" + contentId,
                        "watcherId=" + watcherId,
                        "exceptionType=IllegalStateException")
                .doesNotContain(event.watcherName(), event.contentTitle(), failureMessage);
    }




    private void assertBatchSource(String source, Map<String, UUID> ids, NotificationSource requestSource) {
        assertThat(batchCaptor.getValue().source()).isEqualTo(source);
        assertThat(batchCaptor.getValue().sourceIds()).containsExactlyInAnyOrderEntriesOf(ids);
        assertThat(batchCaptor.getValue().requests()).allSatisfy(request -> assertThat(request.source()).isEqualTo(requestSource));
    }

    private UserFollowedEvent createUserFollowedEvent(String followerName) {
        return new UserFollowedEvent(UUID.randomUUID(), followerName, UUID.randomUUID());
    }

}
