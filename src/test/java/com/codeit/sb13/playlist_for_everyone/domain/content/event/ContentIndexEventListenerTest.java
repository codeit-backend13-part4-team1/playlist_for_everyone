package com.codeit.sb13.playlist_for_everyone.domain.content.event;

import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexEventListener;
import com.codeit.sb13.playlist_for_everyone.domain.content.event.elasticsearch.ContentIndexUpsertEvent;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentIndexEventListener 테스트")
class ContentIndexEventListenerTest {

    @Mock
    private ContentIndexService contentIndexService;

    @InjectMocks
    private ContentIndexEventListener contentIndexEventListener;

    @Test
    @DisplayName("콘텐츠 생성·수정 이벤트 수신 시 문서를 저장한다.")
    void handleUpsert_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexUpsertEvent event =
                new ContentIndexUpsertEvent(contentId);

        // when
        contentIndexEventListener.handleUpsert(event);

        // then
        verify(contentIndexService).upsert(contentId);
        verifyNoMoreInteractions(contentIndexService);
    }

    @Test
    @DisplayName("콘텐츠 삭제 이벤트 수신 시 문서를 삭제한다.")
    void handleDelete_success() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentIndexDeleteEvent event =
                new ContentIndexDeleteEvent(contentId);

        // when
        contentIndexEventListener.handleDelete(event);

        // then
        verify(contentIndexService).delete(contentId);
        verifyNoMoreInteractions(contentIndexService);
    }
}
