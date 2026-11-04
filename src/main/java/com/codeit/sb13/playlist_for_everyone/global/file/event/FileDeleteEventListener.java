package com.codeit.sb13.playlist_for_everyone.global.file.event;

import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FileDeleteEventListener {

    private final FileStorage fileStorage;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(FileDeleteEvent event) {
        fileStorage.delete(event.key());
    }

}
