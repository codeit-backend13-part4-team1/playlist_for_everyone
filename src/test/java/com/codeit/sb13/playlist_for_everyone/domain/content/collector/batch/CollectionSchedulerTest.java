package com.codeit.sb13.playlist_for_everyone.domain.content.collector.batch;

import com.codeit.sb13.playlist_for_everyone.domain.content.collector.config.CollectionScheduleProperties;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CollectionSchedulerTest {

    @Test
    void 설정한_페이지_수로_TMDB_수집을_요청한다() throws Exception {
        var launcher = mock(CollectionJobLauncher.class);
        var properties = new CollectionScheduleProperties();
        properties.getTmdb().setMaxPages(7);

        var scheduler = new CollectionScheduler(launcher, properties);

        scheduler.collectTmdb();

        verify(launcher).startTmdb(7L);
        verifyNoMoreInteractions(launcher);
    }

    @Test
    void 설정한_리그와_시즌으로_스포츠_수집을_요청한다() throws Exception {
        var launcher = mock(CollectionJobLauncher.class);
        var properties = new CollectionScheduleProperties();
        properties.getSportsdb().setLeagueId("4328");
        properties.getSportsdb().setSeason("2014-2015");

        var scheduler = new CollectionScheduler(launcher, properties);

        scheduler.collectSportsDb();

        verify(launcher).startSportsDb("4328", "2014-2015");
        verifyNoMoreInteractions(launcher);
    }
}