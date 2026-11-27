package com.codeit.sb13.playlist_for_everyone.domain.content.runner;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("콘텐츠 초기 인덱싱 Runner 테스트")
class ContentIndexRunnerTest {

    @Mock
    private ContentIndexService contentIndexService;

    @InjectMocks
    private ContentIndexRunner contentIndexRunner;

    @Test
    @DisplayName("Runner 실행 시 콘텐츠 초기 인덱싱을 수행한다.")
    void run_success() throws Exception {
        // given
        var args = new DefaultApplicationArguments();

        // when
        contentIndexRunner.run(args);

        // then
        verify(contentIndexService).indexAll();
    }
}