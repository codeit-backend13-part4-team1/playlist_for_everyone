package com.codeit.sb13.playlist_for_everyone.domain.content.runner;

import com.codeit.sb13.playlist_for_everyone.domain.content.service.elasticsearch.ContentIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "everyone.elasticsearch.index",
        name = "initialize",
        havingValue ="true"
)
public class ContentIndexRunner implements ApplicationRunner {

    private final ContentIndexService contentIndexService;

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        log.info("콘텐츠 초기 인덱싱 시작");

        contentIndexService.indexAll();

        log.info("콘텐츠 초기 인덱싱 종료");
    }
}
