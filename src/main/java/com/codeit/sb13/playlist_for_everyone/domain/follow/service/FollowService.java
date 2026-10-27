package com.codeit.sb13.playlist_for_everyone.domain.follow.service;

import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.FollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.command.UnFollowCommand;
import com.codeit.sb13.playlist_for_everyone.domain.follow.dto.respsone.FollowResult;

public interface FollowService {

    FollowResult follow(FollowCommand command);

    void unfollow(UnFollowCommand command);
}
