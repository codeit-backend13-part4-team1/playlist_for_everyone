package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.NotificationLevel;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 저장소·이벤트 발행자를 Mock으로 대체하여 조회 결과 구성, 생성 이벤트 발행 요청과 읽음 처리를 검증한다.
 *
 * <p>읽음 처리는 저장소가 반환한 알림이 본인 소유일 때만 delete를 호출하는지 확인한다.
 * 타인의 알림이나 빈 조회 결과에서는 삭제하지 않는다. 실제 잠금 대기와 삭제 커밋은
 * NotificationServiceIntegrationTest에서 검증한다.
 *
 * <p>저장 테스트의 ID·생성시각은 직접 준비하므로 JPA 생성 전략·Auditing·실제 커밋을 검증하지 않는다.
 * 해당 동작과 커밋 후 SSE 발행 시점은 NotificationIntegrationTest에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("알림 서비스")
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<NotificationCreatedEvent> eventCaptor;

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

    @Test
    @DisplayName("알림을 저장하면 알림 정보로 생성이벤트를 한 번 발행한다.")
    void publishesCreatedEventOnceWithSavedNotification(){
        UUID notificationId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 2, 10, 0);

        NotificationCommand command = new NotificationCommand(receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO);
        Notification savedNotification = createNotification(notificationId, receiverId, createdAt);

        // 저장소가 돌려준 엔티티를 기준으로 DTO를 구성하는지 확인한다. 실제 INSERT는 실행하지 않는다.
        when(notificationRepository.save(any(Notification.class)))
                .thenReturn(savedNotification);

        notificationService.save(command);

        verify(eventPublisher).publishEvent(eventCaptor.capture());

        NotificationCreatedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.eventId())
                .isNotNull()
                .isNotEqualTo(notificationId);

        assertThat(publishedEvent.notificationDto())
                .isEqualTo(new NotificationDto(
                        notificationId,
                        createdAt,
                        receiverId,
                        "알림 제목",
                        "알림 내용",
                        NotificationLevel.INFO
                ));
    }

    @Test
    @DisplayName("다른 사용자의 알림을 읽음 처리 해도 삭제하지 않고 정상 종료한다.")
    void confirmDoesNotDeleteAnotherReceiversNotification(){
        UUID notificationId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Notification notification = createNotification(
                notificationId,
                ownerId,
                LocalDateTime.of(2026, 11, 4, 10, 0)
        );

        when(notificationRepository.findWithPessimisticLockById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.confirm(notificationId,requesterId);

        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    @Test
    @DisplayName("본인 알림을 읽음 처리하면 조회한 알림을 삭제한다.")
    void confirmDeletesOwnNotification(){
        UUID notificationId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Notification notification = createNotification(notificationId,
                receiverId,
                LocalDateTime.of(2026, 11, 4, 10, 0)
        );

        when(notificationRepository.findWithPessimisticLockById(notificationId))
                .thenReturn(Optional.of(notification));

        notificationService.confirm(notificationId, receiverId);

        verify(notificationRepository).delete(notification);
    }

    @Test
    @DisplayName("존재하지 않는 알림을 읽음 처리하면 삭제 없이 정상 종료한다.")
    void confirmDoesNothingWhenNotificationIsMissing(){
        UUID notificationId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        when(notificationRepository.findWithPessimisticLockById(notificationId))
                .thenReturn(Optional.empty());

        notificationService.confirm(notificationId, receiverId);
        verify(notificationRepository, never()).delete(any(Notification.class));
    }



    /** Mock 저장·조회 결과를 준비한다. 실제 UUID 생성이나 JPA Auditing 실행을 흉내 내는 테스트는 아니다. */
    private Notification createNotification(UUID notificationId, UUID receiverId, LocalDateTime createdAt){
        Notification notification = new Notification(new NotificationCommand(receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO));

        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);

        return notification;
    }
}
