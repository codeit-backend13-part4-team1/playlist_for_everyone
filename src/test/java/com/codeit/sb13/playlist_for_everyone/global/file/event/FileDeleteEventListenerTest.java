package com.codeit.sb13.playlist_for_everyone.global.file.event;

import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3 파일 삭제 이벤트 리스너 테스트")
public class FileDeleteEventListenerTest {

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private FileDeleteEventListener listener;

    @Test
    @DisplayName("파일 삭제 이벤트를 처리하면 S3 파일이 삭제된다.")
    void handle_success() {
        // given
        String key = "test.png";

        FileDeleteEvent event = new FileDeleteEvent(key);

        // when
        listener.handle(event);

        // then
        verify(fileStorage).delete(key);
    }

}
