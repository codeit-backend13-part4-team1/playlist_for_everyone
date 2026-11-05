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

    /**
     * DB 트랜잭션 커밋 이후 S3 파일을 삭제한다.
     *
     * <p>S3 삭제에 실패하더라도 이미 완료된 DB 트랜잭션에
     * 영향을 주지 않도록 예외를 기록하고 전파하지 않는다.
     *
     * @param event 삭제할 파일의 S3 키를 포함한 이벤트
     */
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
