package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.WatchingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class DpeWatchingSessionServiceImpl implements DpeWatchingSessionService {

    private final WatchingSessionRepository watchingSessionRepository;

    @Override
    public long getCurrentViewerCount(UUID contentId){
        return watchingSessionRepository.getWatcherCount(contentId.toString());
    }

    @Override
    public Map<String, Long> getCurrentViewerCountWithContents(List<UUID> contentIds){
        return null;
    }

}
