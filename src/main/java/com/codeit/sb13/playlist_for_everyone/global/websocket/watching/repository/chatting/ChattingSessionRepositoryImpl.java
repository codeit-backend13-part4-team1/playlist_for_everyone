package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.chatting;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ChattingSessionRoom;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChattingSessionRepositoryImpl implements ChattingSessionRepository {
    private final Map<UUID, ChattingSessionRoom> rooms = new ConcurrentHashMap<>();


    @Override
    public void remove(ChattingSessionRoom chattingSessionRoom) {
        rooms.remove(chattingSessionRoom.getContentId(), chattingSessionRoom);
    }

    @Override
    public ChattingSessionRoom findOrCreate(UUID contentId) {
        return rooms.compute(contentId, (id, room) -> {
            if (room == null) return new ChattingSessionRoom(contentId);
            return room;
        });
    }

    @Override
    public Optional<ChattingSessionRoom> find(UUID contentId) {
        return Optional.ofNullable(rooms.get(contentId));
    }
}
