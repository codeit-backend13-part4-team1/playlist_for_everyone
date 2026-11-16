package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "everyone.collection.schedule")
public class CollectionScheduleProperties {

    private boolean enabled = false;
    private String zone = "Asia/Seoul";

    @Valid
    private Tmdb tmdb = new Tmdb();

    @Valid
    private SportsDb sportsdb = new SportsDb();

    @Getter
    @Setter
    public static class Tmdb {
        private String cron = "-";

        @Min(value = 1, message = "maxPages는 1 이상이어야 합니다.")
        @Max(value = 500, message = "maxPages는 500 이하여야 합니다.")
        private long maxPages = 5;

        @AssertTrue(message = "cron은 올바른 실행 시간 형식이어야 합니다.")
        public boolean isCronValid() {
            return "-".equals(cron) || CronExpression.isValidExpression(cron);
        }
    }

    @Getter
    @Setter
    public static class SportsDb {
        private String cron = "-";
        private String leagueId;
        private String season;

        @AssertTrue(message = "cron은 올바른 실행 시간 형식이어야 합니다.")
        public boolean isCronValid() {
            return "-".equals(cron) || CronExpression.isValidExpression(cron);
        }
    }

    @AssertTrue(message = "zone은 올바른 시간대여야 합니다.")
    public boolean isZoneValid() {
        if (zone == null || zone.isBlank()) {
            return false;
        }

        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    @AssertTrue(message = "스포츠 자동 수집에는 리그 ID와 시즌이 필요합니다.")
    public boolean isSportsDbCollectionValid() {
        if (!enabled || "-".equals(sportsdb.getCron())) {
            return true;
        }

        return StringUtils.hasText(sportsdb.getLeagueId())
                && StringUtils.hasText(sportsdb.getSeason());
    }
}