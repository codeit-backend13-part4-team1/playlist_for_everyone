package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 서비스 조회")
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("수신자의 알림이 없으면 빈 목록과 마지막 페이지 정보를 반환한다.")
    void returnsEmptyPageWhenNoNotificationsExist(){
        UUID receiverId = UUID.randomUUID();

        NotificationSearchCondition condition = new NotificationSearchCondition(
                null,
                null,
                20,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        when(notificationRepository.search(condition))
                .thenReturn(List.of());

        when(notificationRepository.searchCount(condition))
                .thenReturn(0L);

        CursorResponse<NotificationDto> response = notificationService.getNotifications(condition);

        assertThat(response.data()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.totalCount()).isZero();
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.sortDirection()).isEqualTo(SortDirection.DESCENDING);

        verify(notificationRepository).search(condition);
        verify(notificationRepository).searchCount(condition);
    }

    @ParameterizedTest(name = "알림 1개, limit={0}이면 마지막 페이지를 반환한다")
    @ValueSource(ints = {1, 20})
    @DisplayName("조회된 알림 수가 limit 이하이면 DTO 목록과 마지막 페이지 정보를 반환한다.")
    void returnsLastPageWhenResultCountDoesNotExceedLimit(int limit){
        UUID receiverId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);

        Notification notification = new Notification(new NotificationCommand(receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO));

        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);

        NotificationSearchCondition condition = new NotificationSearchCondition(null, null, limit, SortDirection.DESCENDING, SortBy.CREATED_AT, receiverId);

        when(notificationRepository.search(condition))
                .thenReturn(List.of(notification));
        when(notificationRepository.searchCount(condition))
                .thenReturn(1L);

        CursorResponse<NotificationDto> response = notificationService.getNotifications(condition);

        assertThat(response.data()).containsExactly(
                new NotificationDto(
                        notificationId,
                        createdAt,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.INFO
                )
        );

        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.totalCount()).isEqualTo(1L);

        verify(notificationRepository).search(condition);
        verify(notificationRepository).searchCount(condition);
    }

    @Test
    @DisplayName("limit보다 1개 더 조회되면 limit개만 반환하고 마지막 응답 알림으로 다음 커서를 생성한다.")
    void returnsNextCursorFromLastReturnedNotification(){
        UUID receiverId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 10, 30);
        Notification first = createNotification(UUID.randomUUID(), receiverId, createdAt);
        Notification second = createNotification(UUID.randomUUID(), receiverId, createdAt.minusMinutes(1));
        Notification third = createNotification(UUID.randomUUID(), receiverId, createdAt.minusMinutes(2));

        NotificationSearchCondition condition = new NotificationSearchCondition(null, null, 2, SortDirection.DESCENDING, SortBy.CREATED_AT, receiverId);
        when(notificationRepository.search(condition))
                .thenReturn(List.of(first,second,third));

        when(notificationRepository.searchCount(condition))
                .thenReturn(10L);

        CursorResponse<NotificationDto> response = notificationService.getNotifications(condition);

        assertThat(response.data())
                .extracting(NotificationDto::id)
                .containsExactly(first.getId(), second.getId());

        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor())
                .isEqualTo(second.getCreatedAt().toString());

        assertThat(response.nextIdAfter()).isEqualTo(second.getId());

        assertThat(response.totalCount()).isEqualTo(10L);

        verify(notificationRepository).search(condition);
        verify(notificationRepository).searchCount(condition);
    }

    @Test
    @DisplayName("커서 이후 조회 결과가 없어도 수신자의 전체 알림 개수를 반환한다.")
    void returnsTotalCountWhenNoNotificationsRemainAfterCursor(){
        UUID receiverId = UUID.randomUUID();

        NotificationSearchCondition condition = new NotificationSearchCondition(
                "2026-10-23T10:30:00",
                UUID.randomUUID(),
                20,
                SortDirection.DESCENDING,
                SortBy.CREATED_AT,
                receiverId
        );

        when(notificationRepository.search(condition))
                .thenReturn(List.of());

        when(notificationRepository.searchCount(condition))
                .thenReturn(10L);

        CursorResponse<NotificationDto> response = notificationService.getNotifications(condition);

        assertThat(response.data()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.nextIdAfter()).isNull();
        assertThat(response.totalCount()).isEqualTo(10L);

        verify(notificationRepository).search(condition);
        verify(notificationRepository).searchCount(condition);

    }



    private Notification createNotification(UUID notificationId, UUID receiverId, LocalDateTime createdAt){
        Notification notification = new Notification(new NotificationCommand(receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO));

        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);

        return notification;
    }
}
