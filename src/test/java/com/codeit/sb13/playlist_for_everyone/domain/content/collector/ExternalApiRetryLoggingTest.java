package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = ExternalApiRetryLoggingTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "logging.level.root=DEBUG"
)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ExternalApiRetryLoggingTest {

    @Test
    void 루트가_DEBUG여도_재시도_로그에_API_키를_남기지_않는다(CapturedOutput output) {
        var retry = new ExternalApiRetry();
        String apiKey = "private-sportsdb-test-key";
        var failure = new ResourceAccessException(
                "요청 실패: https://sportsdb.test/" + apiKey + "/eventsseason.php",
                new IOException("통신 오류")
        );

        assertThat(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).isDebugEnabled())
                .isTrue();

        assertThatThrownBy(() -> retry.execute(() -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(output.getAll()).doesNotContain(apiKey);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
    }
}