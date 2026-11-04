package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SportsDbContentMapperTest {

    @Test
    void 스포츠_응답을_저장용_데이터로_변환한다() {
        var event = new SportsDbEventResponse(
                "441425",
                "West Ham vs Tottenham",
                "Soccer",
                "English Premier League",
                "2014-2015",
                "2014-08-16",
                "https://example.com/match.jpg"
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.type()).isEqualTo(ContentType.sport);
        assertThat(data.provider()).isEqualTo(ContentSource.THE_SPORTS_DB);
        assertThat(data.providerItemId()).isEqualTo("441425");
        assertThat(data.title()).isEqualTo("West Ham vs Tottenham");
        assertThat(data.thumbnailUrl())
                .isEqualTo("https://example.com/match.jpg");
    }

    @Test
    void 종목과_리그를_태그로_변환한다() {
        var event = new SportsDbEventResponse(
                "441425",
                "West Ham vs Tottenham",
                "Soccer",
                "English Premier League",
                "2014-2015",
                "2014-08-16",
                null
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.tags())
                .containsExactlyInAnyOrder("Soccer", "English Premier League");
    }
}