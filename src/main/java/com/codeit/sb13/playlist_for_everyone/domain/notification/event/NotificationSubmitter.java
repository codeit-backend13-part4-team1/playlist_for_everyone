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
        Optional<NotificationDeduplicationStore.Reservation> reservation = store.tryReserve(actionKey);
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
