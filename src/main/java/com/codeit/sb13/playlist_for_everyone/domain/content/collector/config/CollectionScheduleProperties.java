package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "everyone.collection.schedule")
public class CollectionScheduleProperties {

    private boolean enabled = false;
    private String zone = "Asia/Seoul";

    private Tmdb tmdb = new Tmdb();
    private SportsDb sportsdb = new SportsDb();

    @Getter
    @Setter
    public static class Tmdb {
        private String cron = "-";
        private long maxPages = 5;
    }

    @Getter
    @Setter
    public static class SportsDb {
        private String cron = "-";
        private String leagueId;
        private String season;
    }
}