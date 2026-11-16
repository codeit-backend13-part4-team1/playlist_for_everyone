package com.codeit.sb13.playlist_for_everyone.domain.content.collector.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionSchedulePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(ValidationAutoConfiguration.class)
                    )
                    .withUserConfiguration(TestConfig.class);

    @Test
    void 자동_수집은_기본적으로_꺼져_있다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            var properties = context.getBean(CollectionScheduleProperties.class);

            assertThat(properties.isEnabled()).isFalse();
        });
    }

    @Test
    void 설정으로_자동_수집을_켤_수_있다() {
        contextRunner
                .withPropertyValues("everyone.collection.schedule.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties =
                            context.getBean(CollectionScheduleProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CollectionScheduleProperties.class)
    static class TestConfig {
    }

    @Test
    void 실행_시간과_수집_범위의_기본값을_확인한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            var properties = context.getBean(CollectionScheduleProperties.class);

            assertThat(properties.getZone()).isEqualTo("Asia/Seoul");
            assertThat(properties.getTmdb().getCron()).isEqualTo("-");
            assertThat(properties.getTmdb().getMaxPages()).isEqualTo(5L);
            assertThat(properties.getSportsdb().getCron()).isEqualTo("-");
            assertThat(properties.getSportsdb().getLeagueId()).isNull();
            assertThat(properties.getSportsdb().getSeason()).isNull();
        });
    }

    @Test
    void 설정한_실행_시간과_수집_범위를_읽는다() {
        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.zone=UTC",
                        "everyone.collection.schedule.tmdb.cron=0 0 3 * * *",
                        "everyone.collection.schedule.tmdb.max-pages=10",
                        "everyone.collection.schedule.sportsdb.cron=0 0 4 * * *",
                        "everyone.collection.schedule.sportsdb.league-id=4328",
                        "everyone.collection.schedule.sportsdb.season=2014-2015"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties =
                            context.getBean(CollectionScheduleProperties.class);

                    assertThat(properties.getZone()).isEqualTo("UTC");
                    assertThat(properties.getTmdb().getCron())
                            .isEqualTo("0 0 3 * * *");
                    assertThat(properties.getTmdb().getMaxPages()).isEqualTo(10L);
                    assertThat(properties.getSportsdb().getCron())
                            .isEqualTo("0 0 4 * * *");
                    assertThat(properties.getSportsdb().getLeagueId())
                            .isEqualTo("4328");
                    assertThat(properties.getSportsdb().getSeason())
                            .isEqualTo("2014-2015");
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 501, 2147483648L})
    void 페이지_수가_범위를_벗어나면_설정_로딩에_실패한다(long maxPages) {
        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.enabled=true",
                        "everyone.collection.schedule.tmdb.max-pages=" + maxPages
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining("maxPages");
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 500})
    void 페이지_수의_최솟값과_최댓값을_허용한다(long maxPages) {
        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.enabled=true",
                        "everyone.collection.schedule.tmdb.max-pages=" + maxPages
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties =
                            context.getBean(CollectionScheduleProperties.class);

                    assertThat(properties.getTmdb().getMaxPages())
                            .isEqualTo(maxPages);
                });
    }

    @ParameterizedTest
    @MethodSource("잘못된_cron_설정")
    void 실행_시간이_잘못되면_설정_로딩에_실패한다(
            String provider,
            String cron
    ) {
        contextRunner
                .withPropertyValues(
                        "everyone.collection.schedule.enabled=true",
                        "everyone.collection.schedule." + provider + ".cron=" + cron,
                        "everyone.collection.schedule.sportsdb.league-id=4328",
                        "everyone.collection.schedule.sportsdb.season=2014-2015"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .hasStackTraceContaining("cron");
                });
    }

    static Stream<Arguments> 잘못된_cron_설정() {
        return Stream.of("tmdb", "sportsdb")
                .flatMap(provider ->
                        Stream.of("", "   ", "invalid", "0 0 25 * * *")
                                .map(cron -> Arguments.of(provider, cron))
                );
    }
}