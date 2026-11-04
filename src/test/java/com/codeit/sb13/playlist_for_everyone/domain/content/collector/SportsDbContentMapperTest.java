package com.codeit.sb13.playlist_for_everyone.domain.content.collector;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.dto.SportsDbEventResponse;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    void 리그와_시즌과_날짜로_경기_설명을_만든다() {
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

        assertThat(data.description())
                .isEqualTo("English Premier League / 2014-2015 / 2014-08-16");
    }

    @Test
    void 리그와_날짜가_없으면_시즌만_설명으로_사용한다() {
        var event = new SportsDbEventResponse(
                "441425", "테스트 경기", "Soccer",
                null, "2014-2015", "   ", null
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.description()).isEqualTo("2014-2015");
        assertThat(data.tags()).containsExactly("Soccer");
        assertThat(data.thumbnailUrl()).isNull();
    }

    @Test
    void 선택_정보가_모두_없어도_변환할_수_있다() {
        var event = new SportsDbEventResponse(
                "441425", "테스트 경기",
                null, null, null, null, null
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.description()).isEmpty();
        assertThat(data.tags()).isEmpty();
        assertThat(data.thumbnailUrl()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 외부_경기_ID가_없으면_변환할_수_없다(String id) {
        var event = new SportsDbEventResponse(
                id, "테스트 경기", "Soccer",
                "English Premier League", "2014-2015", "2014-08-16",
                null
        );

        assertThatThrownBy(() -> new SportsDbContentMapper().fromEvent(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TheSportsDB 경기 ID가 필요합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 썸네일_주소가_없으면_null로_변환한다(String thumbnailUrl) {
        var event = new SportsDbEventResponse(
                "441425", "테스트 경기", "Soccer",
                "English Premier League", "2014-2015", "2014-08-16",
                thumbnailUrl
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.thumbnailUrl()).isNull();
    }

    @Test
    void 썸네일_주소가_있으면_그대로_유지한다() {
        String thumbnailUrl = "https://example.com/image.jpg";

        var event = new SportsDbEventResponse(
                "441425", "테스트 경기", "Soccer",
                "English Premier League", "2014-2015", "2014-08-16",
                thumbnailUrl
        );

        var data = new SportsDbContentMapper().fromEvent(event);

        assertThat(data.thumbnailUrl()).isEqualTo(thumbnailUrl);
    }
}