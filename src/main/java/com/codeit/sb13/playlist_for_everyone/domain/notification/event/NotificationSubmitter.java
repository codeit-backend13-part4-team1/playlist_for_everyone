package com.codeit.sb13.playlist_for_everyone.domain.notification.event;

import com.codeit.sb13.playlist_for_everyone.domain.notification.dto.event.NotificationPublishBatch;
import com.codeit.sb13.playlist_for_everyone.domain.notification.repository.impl.NotificationDeduplicationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSubmitter {

    private final NotificationAsyncProcessor processor;
    private final NotificationDeduplicationStore store;

    public void submit(String actionKey, NotificationPublishBatch batch){
        Optional<NotificationDeduplicationStore.Reservation> reservation;
        try {
            reservation = store.tryReserve(actionKey);
        } catch (DataAccessException failure) {
            log.warn("{} 알림 Redis 선점 실패로 중복 제한 없이 작업 제출을 시도합니다. sourceIds={}, exceptionType={}",
                    batch.source(), batch.sourceIds(), failure.getClass().getSimpleName());
            processor.publish(batch);
            return;
        }

        if (reservation.isEmpty()) {
            return;
        }

        try {
            processor.publish(batch);
        } catch (TaskRejectedException rejected){
            releaseReservation(reservation.get());
            throw rejected;
        }
    }

    private void releaseReservation(NotificationDeduplicationStore.Reservation reservation) {
        try {
            store.release(reservation);
        }catch (DataAccessException failure){
            log.warn("반복 알림 선점 키 해제에 실패했습니다.", failure);
        }
    }
}
