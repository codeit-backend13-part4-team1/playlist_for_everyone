package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class ExternalApiRetry {

    private final RetryTemplate retryTemplate = new RetryTemplate(
            RetryPolicy.builder()
                    .maxRetries(2)
                    .delay(Duration.ofSeconds(1))
                    .multiplier(2)
                    .predicate(ExternalApiRetry::shouldRetry)
                    .build()
    );

    private static boolean shouldRetry(Throwable exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();

            return status == 500
                    || status == 502
                    || status == 503
                    || status == 504;
        }

        if (exception instanceof ResourceAccessException e) {
            return e.contains(SocketTimeoutException.class)
                    || e.contains(HttpTimeoutException.class)
                    || e.contains(ConnectException.class);
        }

        if (exception instanceof RestClientException e) {
            return e.contains(SocketTimeoutException.class)
                    || e.contains(HttpTimeoutException.class);
        }

        return false;
    }

    public <T> T execute(Supplier<T> request) {
        return retryTemplate.invoke(request);
    }
}
