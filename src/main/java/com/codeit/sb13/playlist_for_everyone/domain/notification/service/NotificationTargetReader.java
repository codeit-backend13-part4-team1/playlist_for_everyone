package com.codeit.sb13.playlist_for_everyone.domain.notification.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.service.FollowService;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NotificationTargetReader {
    private final FollowService followService;
    private final PlaylistService playlistService;

    public List<UUID> findFollowerIds(UUID followeeId){
        return followService.findFollowerIds(followeeId);
    }

    public List<UUID> findSubscriberIds(UUID playlistId){
        return playlistService.findSubscriberIds(playlistId);
    }
}
