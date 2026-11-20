package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventsResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;

@Component
public class SportsDbClient {

    private final RestClient restClient;


    private static final RetryTemplate API_RETRY = new RetryTemplate(
            RetryPolicy.builder()
                    .maxRetries(2)
                    .delay(Duration.ofSeconds(1))
                    .multiplier(2)
                    .predicate(SportsDbClient::shouldRetry)
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
            return !e.contains(UnknownHostException.class)
                    && !e.contains(UnresolvedAddressException.class);
        }

        if (exception instanceof RestClientException e) {
            return e.contains(SocketTimeoutException.class)
                    || e.contains(HttpTimeoutException.class);
        }

        return false;
    }



    public SportsDbClient(
            @Qualifier("sportsDbRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    /** 리그와 시즌에 해당하는 경기 목록 조회 */
    public SportsDbEventsResponse getEventsBySeason(
            String leagueId,
            String season
    ) {
        if (!StringUtils.hasText(leagueId)) {
            throw new IllegalArgumentException("리그 ID는 필수입니다.");
        }

        if (!StringUtils.hasText(season)) {
            throw new IllegalArgumentException("시즌은 필수입니다.");
        }


        try {
            return API_RETRY.invoke(() -> restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsseason.php")
                            .queryParam("id", leagueId)
                            .queryParam("s", season)
                            .build())
                    .retrieve()
                    .body(SportsDbEventsResponse.class));

        } catch (ResourceAccessException e) {
            String code = networkErrorCode(e);
            throw new ResourceAccessException("TheSportsDB 요청 실패: " + code);

        } catch (RestClientException e) {
            if (e.contains(SocketTimeoutException.class)
                    || e.contains(HttpTimeoutException.class)) {
                throw new ResourceAccessException(
                        "TheSportsDB 요청 실패: TIMEOUT"
                );
            }

            throw e;
        }

    }

    private String networkErrorCode(ResourceAccessException e) {
        if (e.contains(HttpTimeoutException.class) || e.contains(SocketTimeoutException.class)) {
            return "TIMEOUT";
        }
        if (e.contains(UnknownHostException.class) || e.contains(UnresolvedAddressException.class)) {
            return "DNS_ERROR";
        }
        if (e.contains(ConnectException.class)) {
            return "CONNECTION_ERROR";
        }
        return "NETWORK_ERROR";
    }

}