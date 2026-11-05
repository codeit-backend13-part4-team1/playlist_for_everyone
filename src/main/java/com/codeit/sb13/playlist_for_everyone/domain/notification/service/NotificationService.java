package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.command.NotificationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.repository.NotificationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.response.NotificationDto;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import com.codeit.sb13.playlist_for_everyone.domain.notification.event.NotificationCreatedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.NotificationRepository;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

/**
 * 알림의 생성·목록 조회·읽음 처리를 조율한다.
 *
 * <p>컨트롤러가 인증 정보에서 추출한 수신자 ID를 전달받아 사용한다.
 * 목록 조회의 수신자 조건은 저장소에서 적용하고, 삭제 대상의 소유자 확인은 서비스에서 수행한다.
 * 이 서비스는 SecurityContext에 직접 의존하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Validated
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 검증된 요청을 독립 트랜잭션으로 저장하고, 커밋 후 전송할 DTO 스냅샷을 이벤트로 발행한다.
     *
     * <p>다른 빈에서 이 메서드를 호출하면 REQUIRES_NEW가 적용된다. 공통 생성 경로에서는
     * 업무 커밋 후 리스너가 호출하므로 알림 저장 실패가 원래 업무의 커밋을 되돌리지 않는다.
     * 이벤트 발행은 이 트랜잭션 안에서 하고, 실제 SSE 발행은 AFTER_COMMIT 리스너에 맡긴다.
     * 이 메서드를 업무 커밋 전에 직접 호출하면 두 저장의 커밋 결과는 독립적이라는 점에 주의한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(@Valid @NotNull NotificationCommand command){
        // 현재 UUID 생성 전략과 JPA Auditing은 신규 엔티티 저장 시 ID·생성시각을 채운다.
        // save 반환은 커밋 완료를 뜻하지 않으며, 스냅샷 생성을 위한 명시적 flush는 강제하지 않는다.
        Notification save = notificationRepository.save(new Notification(command));
        // 이벤트 UUID는 여기서 한 번 생성한다. 이후 커밋이 실패하면 전송 리스너는 실행되지 않는다.
        eventPublisher.publishEvent(new NotificationCreatedEvent(UUID.randomUUID(), NotificationDto.from(save)));
    }

    /**
     * 저장소의 limit + 1건 조회 결과로 다음 페이지 유무를 판단하고, 실제 반환할 항목만 DTO로 만든다.
     *
     * <p>totalCount는 커서 이후 남은 수가 아니라 해당 수신자의 전체 알림 수다.
     * 다음 페이지가 있으면 반환 목록의 마지막 항목에서 시각·ID를 함께 추출하며,
     * 마지막 페이지와 빈 목록에서는 두 커서 값 모두 null을 반환한다.
     */
    @Transactional(readOnly = true)
    public CursorResponse<NotificationDto> getNotifications(NotificationSearchCondition condition){

        List<Notification> notifications = notificationRepository.search(condition);
        long totalCount = notificationRepository.searchCount(condition);

        boolean hasNext = notifications.size() > condition.limit();

        List<NotificationDto> data = retrieveNotifications(hasNext, notifications)
                .stream()
                .map(NotificationDto::from)
                .toList();

        return new CursorResponse<>(
                data,
                getNextCursor(hasNext, data),
                getNextIdAfter(hasNext, data),
                hasNext,
                totalCount,
                condition.sortBy().getSortKey(),
                condition.sortDirection()
        ) ;
    }

    /**
     * 알림을 비관적 잠금으로 조회하고, 전달받은 수신자의 알림이면 삭제하여 읽음 처리한다.
     *
     * <p>클래스의 @Transactional에 따라 잠금 조회·소유자 확인·삭제 요청을 같은 트랜잭션에서 수행한다.
     * 잠금은 해당 트랜잭션이 종료될 때 해제되며, 삭제 확정은 커밋 시점에 이루어진다.
     * 외부 트랜잭션에 참여한 경우에는 이 메서드가 반환되어도 잠금과 미커밋 상태가 유지될 수 있다.
     *
     * <p>대상이 없거나 타인의 알림이면 삭제하지 않고 정상 종료하여 컨트롤러가 204를 반환하게 한다.
     * 잠금 획득·DB 처리 중 발생한 예외는 그대로 전파한다. 204는 정상 처리 결과이며,
     * 실제로 삭제된 행이 있었다는 의미는 아니다.
     */
    public void confirm(UUID notificationId, UUID receiverId){
        notificationRepository.findWithPessimisticLockById(notificationId)
                .filter(notification -> notification.isOwner(receiverId))
                .ifPresent(notificationRepository::delete);
    }

    private UUID getNextIdAfter(boolean hasNext, List<NotificationDto> data) {
        if (hasNext) {
            return data.get(data.size() - 1).id();
        }
        return null;
    }

    private String getNextCursor(boolean hasNext, List<NotificationDto> data) {
        if (hasNext){
            return data.get(data.size() - 1).createdAt().toString();
        }
        return null;
    }

    private List<Notification> retrieveNotifications(boolean hasNext, List<Notification> notifications) {
        if (hasNext) {
            return notifications.subList(0, notifications.size() - 1);
        }
        return notifications;
    }

}
