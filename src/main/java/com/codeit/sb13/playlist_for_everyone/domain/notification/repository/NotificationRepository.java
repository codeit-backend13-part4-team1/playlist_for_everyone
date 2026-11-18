package com.codeit.sb13.playlist_for_everyone.domain.notification.repository;

import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification , UUID> , NotificationCustomRepository{

    /**
     * 알림 ID로 비관적 쓰기 락을 획득하여 조회한다.
     *
     * <p>서비스의 트랜잭션 안에서 호출하며, 조회한 행의 잠금은 해당 트랜잭션이 종료될 때까지 유지된다.
     * 조회 조건은 알림 ID만 사용한다. 조회 결과의 소유자 확인과 삭제 요청은 서비스에서 수행한다.
     * 대상이 없으면 빈 Optional을 반환한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.id = :notificationId
    """)
    Optional<Notification> findWithPessimisticLockById(@Param("notificationId") UUID notificationId);

}
