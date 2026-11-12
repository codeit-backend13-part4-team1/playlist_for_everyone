package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbClientConfig;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SportsDbClientTimeoutTest {

    @Test
    @Timeout(10)
    void 응답이_늦으면_읽기_시간_초과가_발생한다() throws Exception {
        var server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        var executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);

        try {
            server.createContext("/test-key/eventsseason.php", exchange -> {
                try {
                    Thread.sleep(3000);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();

            var properties = new SportsDbProperties();
            properties.setBaseUrl(
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );
            properties.setApiKey("test-key");
            properties.setConnectTimeout(Duration.ofSeconds(3));
            properties.setReadTimeout(Duration.ofSeconds(1));

            var config = new SportsDbClientConfig();
            var restClient = config.sportsDbRestClient(
                    RestClient.builder(),
                    properties
            );
            var client = new SportsDbClient(restClient);

            assertThatThrownBy(() -> client.getEventsBySeason("4328", "2014-2015"))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasMessageContaining("TIMEOUT")
                    .hasMessageNotContaining("test-key")
                    .hasNoCause();
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void 본문_전송이_늦으면_TIMEOUT으로_분류한다() throws Exception {
        var server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        var executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);

        try {
            server.createContext("/test-key/eventsseason.php", exchange -> {
                try {
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/json");

                    exchange.sendResponseHeaders(200, 0);

                    var body = exchange.getResponseBody();

                    // 응답 일부를 먼저 전송
                    body.write("{\"events\":[".getBytes(StandardCharsets.UTF_8));
                    body.flush();

                    // 읽기 제한 시간인 1초보다 오래 대기
                    Thread.sleep(3000);

                    body.write("]}".getBytes(StandardCharsets.UTF_8));
                    body.flush();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();

            var properties = new SportsDbProperties();
            properties.setBaseUrl(
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );
            properties.setApiKey("test-key");
            properties.setConnectTimeout(Duration.ofSeconds(3));
            properties.setReadTimeout(Duration.ofSeconds(1));

            var config = new SportsDbClientConfig();
            var restClient = config.sportsDbRestClient(
                    RestClient.builder(),
                    properties
            );
            var client = new SportsDbClient(restClient);

            assertThatThrownBy(() -> client.getEventsBySeason("4328", "2014-2015"))
                    .isInstanceOf(ResourceAccessException.class)
                    .hasMessageContaining("TIMEOUT")
                    .hasMessageNotContaining("test-key")
                    .hasNoCause();
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}