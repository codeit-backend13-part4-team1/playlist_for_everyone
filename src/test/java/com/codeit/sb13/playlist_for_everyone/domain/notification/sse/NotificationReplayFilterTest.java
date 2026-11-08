package com.codeit.sb13.playlist_for_everyone.domain.notification.sse;

import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.sse.event.SseEvent;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryEntry;
import com.codeit.sb13.playlist_for_everyone.global.sse.history.SseHistoryPosition;
import com.codeit.sb13.playlist_for_everyone.global.sse.replay.SseReplayFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 재전송 필터")
class NotificationReplayFilterTest {

    @Mock
    private NotificationRepository notificationRepository;

    private SseReplayFilter replayFilter;
    private final JsonMapper jsonMapper= JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        replayFilter = new NotificationReplayFilter(notificationRepository);
    }

    @Test
    @DisplayName("DB 알림 ID를 일괄 조회하여 남아 잇는 알림 이력만 원래 순서대로 반환한다.")
    void retainsExistingNotificationsInOriginalHistoryOrder(){
        UUID receiverId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        UUID lastId = UUID.randomUUID();

        SseHistoryEntry firstEntry = createEntry(receiverId, firstId, "1000-0");
        SseHistoryEntry deletedEntry = createEntry(receiverId, deletedId, "1000-1");
        SseHistoryEntry lastEntry = createEntry(receiverId, lastId, "1000-2");

        List<SseHistoryEntry> entries = List.of(firstEntry, deletedEntry, lastEntry);
        List<UUID> notificationIds = List.of(firstId, deletedId, lastId);
        when(notificationRepository.findExistingIds(receiverId, notificationIds))
                .thenReturn(List.of( lastId, firstId));

        List<SseHistoryEntry> result = replayFilter.filter(receiverId, entries);

        assertThat(result).containsExactly(firstEntry, lastEntry);
        verify(notificationRepository).findExistingIds(receiverId, notificationIds);
        verifyNoMoreInteractions(notificationRepository);


    }

    @Test
    @DisplayName("수신자에게 존재하는 알림 ID가 없으면 빈 이력 목록을 반환한다.")
    void returnsEmptyWhenNoNotificationsExistForReceiver(){
        UUID receiverId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        List<SseHistoryEntry> entries = List.of(
                createEntry(receiverId, firstId, "1000-0"),
                createEntry(receiverId, secondId, "1000-1")
        );

        List<UUID> notificationIds = List.of(firstId, secondId);

        when(notificationRepository.findExistingIds(receiverId, notificationIds))
                .thenReturn(List.of());

        List<SseHistoryEntry> result = replayFilter.filter(receiverId, entries);

        assertThat(result).isEmpty();
        verify(notificationRepository).findExistingIds(receiverId, notificationIds);
        verifyNoMoreInteractions(notificationRepository);

    }

    @Test
    @DisplayName("알림 존재 여부 조회에 실패하면 원래 예외를 그대로 전달한다.")
    void propagatesNotificationLookupFailure(){
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        List<SseHistoryEntry> entries = List.of(createEntry(receiverId, notificationId, "1000-0"));
        List<UUID> notificationIds = List.of(notificationId);

        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("알림 조회에 실패했습니다.");

        when(notificationRepository.findExistingIds(receiverId, notificationIds))
                .thenThrow(failure);
        assertThatThrownBy(() -> replayFilter.filter(receiverId, entries))
                .isSameAs(failure);

        verify(notificationRepository).findExistingIds(receiverId, notificationIds);
        verifyNoMoreInteractions(notificationRepository);

    }

    private SseHistoryEntry createEntry(UUID receiverId, UUID notificationId, String position){
        return new SseHistoryEntry(new SseHistoryPosition(position),
                SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, jsonMapper.createObjectNode()
                        .put("id", notificationId.toString())));
    }
}