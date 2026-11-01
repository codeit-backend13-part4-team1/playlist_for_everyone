package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.TmdbClientConfig;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.TmdbProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmdbClientTimeoutTest {

    @Test
    @Timeout(10)
    void 응답이_늦으면_읽기_시간_초과가_발생한다() throws Exception {
        // 사용 가능한 포트를 자동으로 배정받아 로컬 서버 생성
        var server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );
        var executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);

        try {
            server.createContext("/3/movie/popular", exchange -> {
                try {
                    // 일부러 응답을 늦춤
                    Thread.sleep(3000);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();

            var properties = new TmdbProperties();
            properties.setBaseUrl(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/3"
            );
            properties.setAccessToken("test-token");
            properties.setConnectTimeout(Duration.ofSeconds(3));
            properties.setReadTimeout(Duration.ofSeconds(1));

            var config = new TmdbClientConfig();
            var restClient = config.tmdbRestClient(
                    RestClient.builder(), properties
            );
            var client = new TmdbClient(restClient);

            assertThatThrownBy(() -> client.getPopularMovies(1))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasCauseInstanceOf(HttpTimeoutException.class);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}