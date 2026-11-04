package com.codeit.sb13.playlist_for_everyone.global.file.event;

import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileDeleteEventListener {

    private final FileStorage fileStorage;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(FileDeleteEvent event) {
        try {
            fileStorage.delete(event.key());
        } catch (RuntimeException e) {
            log.error("DB 커밋 후 S3 파일 삭제 실패. key={}", event.key(), e);
        }

    }

}
