package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
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

    @ParameterizedTest
    @MethodSource("nestedDnsErrors")
    void 연결_오류에_DNS_원인이_있으면_재시도하지_않는다(Throwable dnsError) {
        AtomicInteger attempts = new AtomicInteger();
        var connectionError = new ConnectException("연결 실패");
        connectionError.initCause(dnsError);

        var failure = new ResourceAccessException("외부 API 요청 실패", connectionError);

        assertThatThrownBy(() -> externalApiRetry.execute(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void DNS_원인이_없는_연결_실패는_최대_두_번_재시도한다() {
        AtomicInteger attempts = new AtomicInteger();
        var failure = new ResourceAccessException(
                "외부 API 요청 실패",
                new ConnectException("연결 거부")
        );

        assertThatThrownBy(() -> externalApiRetry.execute(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts.get()).isEqualTo(3);
    }

    private static Stream<Throwable> nestedDnsErrors() {
        return Stream.of(
                new UnknownHostException("호스트를 찾을 수 없습니다."),
                new UnresolvedAddressException()
        );
    }
}
