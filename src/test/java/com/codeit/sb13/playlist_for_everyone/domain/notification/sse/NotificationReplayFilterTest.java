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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidNotificationPayloads")
    @DisplayName("잘못된 알림 데이터가 섞여 있어도 정상 알림만 원래 순서대로 반환한다.")
    void skipsInvalidNotificationIdsAndPreservesHistoryOrder(String description, String payloadJson) {
        UUID receiverId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID lastId = UUID.randomUUID();

        SseHistoryEntry firstEntry = createEntry(receiverId, firstId, "1000-0");
        SseHistoryEntry invalidEntry = createEntryWithPayload(receiverId, payloadJson, "1000-1");
        SseHistoryEntry lastEntry = createEntry(receiverId, lastId, "1000-2");

        List<UUID> notificationIds = List.of(firstId, lastId);
        when(notificationRepository.findExistingIds(receiverId, notificationIds))
                .thenReturn(List.of(lastId, firstId));

        List<SseHistoryEntry> result = replayFilter.filter(
                receiverId, List.of(firstEntry, invalidEntry, lastEntry));

        assertThat(result).containsExactly(firstEntry, lastEntry);
        verify(notificationRepository).findExistingIds(receiverId, notificationIds);
        verifyNoMoreInteractions(notificationRepository);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidNotificationPayloads")
    @DisplayName("모든 알림 데이터가 비정상이면 DB 조회 없이 빈 이력 목록을 반환한다.")
    void returnsEmptyWithoutLookupWhenAllNotificationIdsAreInvalid(String description, String payloadJson) {
        UUID receiverId = UUID.randomUUID();
        SseHistoryEntry invalidEntry = createEntryWithPayload(receiverId, payloadJson, "1000-0");

        List<SseHistoryEntry> result = replayFilter.filter(receiverId, List.of(invalidEntry));

        assertThat(result).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    private static Stream<Arguments> invalidNotificationPayloads() {
        return Stream.of(
                Arguments.of("데이터 참조 없음", (String) null),
                Arguments.of("JSON null", "null"),
                Arguments.of("ID 누락", "{}"),
                Arguments.of("ID가 null", "{\"id\":null}"),
                Arguments.of("ID가 비문자열", "{\"id\":123}"),
                Arguments.of("UUID 형식 오류", "{\"id\":\"not-a-uuid\"}")
        );
    }

    private SseHistoryEntry createEntryWithPayload(UUID receiverId, String payloadJson, String position) {
        return new SseHistoryEntry(
                new SseHistoryPosition(position),
                SseEvent.createNotificationEvent(
                        UUID.randomUUID(), receiverId,
                        payloadJson == null ? null : jsonMapper.readTree(payloadJson))
        );
    }

    private SseHistoryEntry createEntry(UUID receiverId, UUID notificationId, String position){
        return new SseHistoryEntry(new SseHistoryPosition(position),
                SseEvent.createNotificationEvent(UUID.randomUUID(), receiverId, jsonMapper.createObjectNode()
                        .put("id", notificationId.toString())));
    }
}
