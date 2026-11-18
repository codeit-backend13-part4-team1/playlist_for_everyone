package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbClientConfig;
import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.SportsDbProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
        named = "RUN_SPORTSDB_LIVE_TEST",
        matches = "true"
)
class SportsDbClientLiveTest {

    @Test
    void 실제_TheSportsDB에서_경기_목록을_조회한다() {
        var properties = new SportsDbProperties();
        properties.setBaseUrl("https://www.thesportsdb.com/api/v1/json");
        properties.setApiKey("123");

        var config = new SportsDbClientConfig();
        var restClient = config.sportsDbRestClient(
                RestClient.builder(),
                properties
        );
        var client = new SportsDbClient(restClient);

        var response = client.getEventsBySeason("4328", "2014-2015");

        assertThat(response).isNotNull();
        assertThat(response.events()).isNotEmpty();

        var event = response.events().get(0);
        assertThat(event.id()).isNotBlank();
        assertThat(event.title()).isNotBlank();

        System.out.println("첫 번째 경기: " + event.title());
    }
}