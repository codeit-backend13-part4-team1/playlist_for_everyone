package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventsResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;

@Component
public class SportsDbClient {

    private final RestClient restClient;

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
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsseason.php")
                            .queryParam("id", leagueId)
                            .queryParam("s", season)
                            .build())
                    .retrieve()
                    .body(SportsDbEventsResponse.class);
        } catch (ResourceAccessException e) {
            String code = networkErrorCode(e);
            throw new ResourceAccessException("TheSportsDB 요청 실패: " + code);
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