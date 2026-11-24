package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcsTaskMetadataConfigTest {

    @Test
    @Timeout(10)
    void 메타데이터_본문이_늦으면_읽기_시간_초과가_발생한다() throws Exception {
        var server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );
        var executor = Executors.newSingleThreadExecutor();
        var releaseResponse = new CountDownLatch(1);

        server.setExecutor(executor);

        try {
            server.createContext("/current/task", exchange -> {
                try {
                    exchange.getResponseHeaders()
                            .set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, 0);

                    var body = exchange.getResponseBody();
                    body.write(
                            "{\"Cluster\":".getBytes(StandardCharsets.UTF_8)
                    );
                    body.flush();

                    releaseResponse.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });

            server.start();

            String metadataUri = "http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/current";

            var config = new EcsTaskMetadataConfig();
            var client = config.ecsTaskMetadataClient(
                    RestClient.builder(),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1)
            );

            assertThatThrownBy(() -> client.getCurrentOwner(metadataUri))
                    .isInstanceOf(RestClientException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        } finally {
            releaseResponse.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}