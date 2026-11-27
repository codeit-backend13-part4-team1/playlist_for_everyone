package com.codeit.sb13.playlist_for_everyone.global.websocket.watch.service.view;

import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionRegisterException;
import com.codeit.sb13.playlist_for_everyone.global.service.lock.Locks;
import com.codeit.sb13.playlist_for_everyone.global.service.redis.RedisTopicRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.entity.WSSubscription;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.ViewSessionMessageListener;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.ViewingSessionRedisRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing.ViewingSessionRepository;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("시청 세션 서비스 기본 동작 테스트")
class ViewingSessionServiceTest {

    private static final UUID CONTENT_ID = new UUID(0L, 1L);
    private static final UUID OTHER_CONTENT_ID = new UUID(0L, 2L);
    private static final UUID USER_ID = new UUID(0L, 3L);
    private static final UUID FIRST_SUBSCRIPTION_KEY = new UUID(0L, 4L);
    private static final UUID SECOND_SUBSCRIPTION_KEY = new UUID(0L, 5L);

    private static final String SESSION_ID = "ws-session-1";
    private static final String FIRST_SUBSCRIPTION_ID = "sub-1";
    private static final String SECOND_SUBSCRIPTION_ID = "sub-2";
    private static final String TOPIC = "viewing-topic-1";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private ViewingSessionRepository roomRepository;

    @Mock
    private ViewSessionMessageListener messageListener;

    @Mock
    private ViewingSessionRedisRepository presenceRepository;

    @Mock
    private RedisTopicRegistry topicRegistry;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private Locks locks;

    @Mock
    private ViewingSessionRoom room;

    @InjectMocks
    private ViewingSessionServiceImpl service;

    @Test
    @DisplayName("이미 시청 중인 사용자도 새 구독으로 입장할 수 있다")
    void joinRegistersAnotherSubscriptionForExistingViewer() {
        WSSubscription subscription = subscription(
                FIRST_SUBSCRIPTION_KEY, FIRST_SUBSCRIPTION_ID
        );
        when(subscription.createdAt()).thenReturn(CREATED_AT);
        stubContentLock();
        when(roomRepository.findOrCreate(CONTENT_ID)).thenReturn(room);
        when(room.topic()).thenReturn(TOPIC);
        when(presenceRepository.register(
                FIRST_SUBSCRIPTION_KEY, CONTENT_ID, USER_ID, CREATED_AT
        )).thenReturn(false);

        service.join(subscription, CONTENT_ID, USER_ID);

        verify(presenceRepository).register(
                FIRST_SUBSCRIPTION_KEY, CONTENT_ID, USER_ID, CREATED_AT
        );
        verify(topicRegistry).register(
                TOPIC, SESSION_ID, FIRST_SUBSCRIPTION_ID, messageListener
        );
        verify(room).join(subscription);
    }

    @Test
    @DisplayName("시청 세션 등록에 실패하면 토픽이나 룸에 구독을 추가하지 않는다")
    void joinPropagatesRegistrationFailureWithoutAddingSubscription() {
        WSSubscription subscription = mock(WSSubscription.class);
        when(subscription.uuid()).thenReturn(FIRST_SUBSCRIPTION_KEY);
        when(subscription.createdAt()).thenReturn(CREATED_AT);
        stubContentLock();
        when(roomRepository.findOrCreate(CONTENT_ID)).thenReturn(room);

        IllegalStateException failure = new IllegalStateException("시청 세션 등록 실패");
        when(presenceRepository.register(
                FIRST_SUBSCRIPTION_KEY, CONTENT_ID, USER_ID, CREATED_AT
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.join(subscription, CONTENT_ID, USER_ID))
                .isSameAs(failure);

        verifyNoInteractions(topicRegistry);
        verify(room, never()).join(subscription);
    }

    @Test
    @DisplayName("퇴장하면 해당 구독과 토픽 등록을 해제한다")
    void leaveUnregistersTheRequestedSubscription() {
        WSSubscription subscription = subscription(
                FIRST_SUBSCRIPTION_KEY, FIRST_SUBSCRIPTION_ID
        );
        stubContentLock();
        stubExistingRoom();

        service.leave(subscription, CONTENT_ID);

        verify(room).leave(subscription);
        verify(presenceRepository).unregister(FIRST_SUBSCRIPTION_KEY);
        verify(topicRegistry).unregister(SESSION_ID, FIRST_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("없는 룸에서 퇴장하면 예외를 반환하고 구독을 건드리지 않는다")
    void leaveFailsForMissingRoom() {
        WSSubscription subscription = mock(WSSubscription.class);
        stubContentLock();
        when(roomRepository.find(CONTENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(subscription, CONTENT_ID))
                .isInstanceOf(WebSocketSessionRegisterException.class);

        verifyNoInteractions(presenceRepository, topicRegistry);
    }

    @Test
    @DisplayName("같은 웹소켓 세션에 속한 여러 구독을 일괄 해제한다")
    void leaveAllUnregistersEveryRequestedSubscription() {
        WSSubscription first = subscription(
                FIRST_SUBSCRIPTION_KEY, FIRST_SUBSCRIPTION_ID
        );
        WSSubscription second = mock(WSSubscription.class);
        when(second.uuid()).thenReturn(SECOND_SUBSCRIPTION_KEY);
        when(second.id()).thenReturn(SECOND_SUBSCRIPTION_ID);

        List<WSSubscription> subscriptions = List.of(first, second);
        stubExistingRoom();

        service.leaveAll(subscriptions, CONTENT_ID);

        verify(room).leaveAll(subscriptions);
        verify(presenceRepository).unregisterAll(
                List.of(FIRST_SUBSCRIPTION_KEY, SECOND_SUBSCRIPTION_KEY)
        );
        verify(topicRegistry).unregisterAll(
                SESSION_ID, List.of(FIRST_SUBSCRIPTION_ID, SECOND_SUBSCRIPTION_ID)
        );
    }

    @Test
    @DisplayName("콘텐츠를 종료하면 구독을 정리하고 기존 구독 목록을 반환한다")
    void destroyRemovesContentAndReturnsItsSubscriptions() {
        WSSubscription first = mock(WSSubscription.class);
        WSSubscription second = mock(WSSubscription.class);
        stubExistingRoom();
        when(room.getAll()).thenReturn(new ArrayList<>(List.of(first, second)));

        List<WSSubscription> destroyed = service.destroy(CONTENT_ID);

        assertThat(destroyed).containsExactlyInAnyOrder(first, second);
        verify(presenceRepository).deleteWithContentId(CONTENT_ID);
        verify(topicRegistry).disconnectWithContentId(CONTENT_ID.toString());
        verify(roomRepository).remove(room);
        verify(room).clear();
    }

    @Test
    @DisplayName("없는 콘텐츠를 종료하면 빈 목록을 반환하고 삭제를 요청하지 않는다")
    void destroyReturnsEmptyForMissingRoom() {
        when(roomRepository.find(CONTENT_ID)).thenReturn(Optional.empty());

        assertThat(service.destroy(CONTENT_ID)).isEmpty();

        verifyNoInteractions(presenceRepository, topicRegistry);
    }

    @Test
    @DisplayName("메시지를 요청한 콘텐츠의 토픽으로 보낸다")
    void sendPublishesPayloadToContentTopic() {
        Map<String, String> payload = Map.of("type", "viewer-count-updated");
        stubExistingRoom();
        when(room.topic()).thenReturn(TOPIC);

        service.send(CONTENT_ID, payload);

        verify(redisTemplate).convertAndSend(TOPIC, payload);
    }

    @Test
    @DisplayName("수명 갱신은 요청한 구독 UUID 전체를 저장소에 전달한다")
    void refreshPassesAllSubscriptionKeys() {
        List<UUID> subscriptionKeys = List.of(
                FIRST_SUBSCRIPTION_KEY, SECOND_SUBSCRIPTION_KEY
        );

        service.refresh(subscriptionKeys);

        verify(presenceRepository).refresh(subscriptionKeys);
    }

    @Test
    @DisplayName("요청한 콘텐츠의 현재 시청자 수를 반환한다")
    void getCurrentViewerCountReturnsStoredCount() {
        when(presenceRepository.getWatcherCount(CONTENT_ID.toString()))
                .thenReturn(4L);

        assertThat(service.getCurrentViewerCount(CONTENT_ID)).isEqualTo(4L);
    }

    @Test
    @DisplayName("여러 콘텐츠의 시청자 수를 조회하고 0명인 콘텐츠도 반환한다")
    void getCurrentViewerCountWithContentsReturnsAllCounts() {
        when(presenceRepository.getWatcherCountWithMultipleContents(
                List.of(CONTENT_ID.toString(), OTHER_CONTENT_ID.toString())
        )).thenReturn(Map.of(CONTENT_ID, 4L, OTHER_CONTENT_ID, 0L));

        assertThat(service.getCurrentViewerCountWithContents(
                List.of(CONTENT_ID, OTHER_CONTENT_ID)
        )).containsExactlyInAnyOrderEntriesOf(
                Map.of(CONTENT_ID, 4L, OTHER_CONTENT_ID, 0L)
        );
    }

    @Test
    @DisplayName("요청한 콘텐츠의 활성 시청 세션 정보를 반환한다")
    void getCurrentViewerIdListReturnsViewingSessionInfo() {
        ViewingSessionInfoDto viewer = new ViewingSessionInfoDto(
                FIRST_SUBSCRIPTION_KEY, CREATED_AT, USER_ID, CONTENT_ID
        );
        when(presenceRepository.currentViewerIdList(CONTENT_ID.toString()))
                .thenReturn(List.of(viewer));

        assertThat(service.getCurrentViewerIdList(CONTENT_ID))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(viewer);
    }

    private WSSubscription subscription(UUID key, String subscriptionId) {
        WSSubscription subscription = mock(WSSubscription.class);
        when(subscription.uuid()).thenReturn(key);
        when(subscription.sessionId()).thenReturn(SESSION_ID);
        when(subscription.id()).thenReturn(subscriptionId);
        return subscription;
    }

    private void stubContentLock() {
        when(locks.lock(CONTENT_ID)).thenReturn(new Object());
    }

    private void stubExistingRoom() {
        when(roomRepository.find(CONTENT_ID)).thenReturn(Optional.of(room));
    }
}