package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.chatting;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ChattingSessionRoom;

import java.util.Optional;
import java.util.UUID;

public interface ChattingSessionRepository {

    void remove(ChattingSessionRoom chattingSessionRoom);

    ChattingSessionRoom findOrCreate(UUID contentId);

    Optional<ChattingSessionRoom> find(UUID contentId);

}
