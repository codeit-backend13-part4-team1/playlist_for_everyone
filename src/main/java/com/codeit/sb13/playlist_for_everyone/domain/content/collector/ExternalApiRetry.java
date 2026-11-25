package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class ExternalApiRetry {

    private final RetryTemplate retryTemplate;

    public ExternalApiRetry() {
        this(Duration.ofSeconds(1));
    }

    ExternalApiRetry(Duration initialDelay) {
        this.retryTemplate = new RetryTemplate(createPolicy(initialDelay));
    }

    static RetryPolicy createPolicy(Duration initialDelay) {
        return RetryPolicy.builder()
                .maxRetries(2)
                .delay(initialDelay)
                .multiplier(2)
                .predicate(ExternalApiRetry::shouldRetry)
                .build();
    }

    public <T> T execute(Supplier<T> request) {
        return retryTemplate.invoke(request);
    }

    private static boolean shouldRetry(Throwable exception) {
        if (exception instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 500 || status == 502
                    || status == 503 || status == 504;
        }

        if (exception instanceof ResourceAccessException e) {
            if (e.contains(UnknownHostException.class)
                    || e.contains(UnresolvedAddressException.class)) {
                return false;
            }
            return e.contains(ConnectException.class)
                    || isTimeout(e);
        }
        if (exception instanceof RestClientException e) {
            return isTimeout(e);
        }
        return false;
    }

    private static boolean isTimeout(RestClientException exception) {
        return exception.contains(SocketTimeoutException.class)
                || exception.contains(HttpTimeoutException.class);
    }

}
