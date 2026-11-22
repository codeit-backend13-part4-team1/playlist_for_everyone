package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalApiRetryTest {

    private final ExternalApiRetry externalApiRetry = new ExternalApiRetry();

    @ParameterizedTest
    @MethodSource("nonRetryableNetworkErrors")
    void 재시도_대상이_아닌_네트워크_오류는_즉시_실패한다(IOException error) {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> externalApiRetry.execute(() -> {
            attempts.incrementAndGet();

            throw new ResourceAccessException("외부 API 요청 실패", error);
        })).isInstanceOf(ResourceAccessException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    private static Stream<IOException> nonRetryableNetworkErrors() {
        return Stream.of(new SSLHandshakeException("인증서 오류"), new IOException("알 수 없는 네트워크 오류"));
    }
}
