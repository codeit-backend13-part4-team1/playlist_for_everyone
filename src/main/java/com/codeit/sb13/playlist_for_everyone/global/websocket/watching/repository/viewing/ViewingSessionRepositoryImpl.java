package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.repository.viewing;

import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.entity.ViewingSessionRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ViewingSessionRepositoryImpl implements ViewingSessionRepository{
    private final Map<UUID, ViewingSessionRoom> rooms = new ConcurrentHashMap<>();


    @Override
    public ViewingSessionRoom findOrCreate(UUID contentId) {
        return rooms.compute(contentId, (id, room) -> {
            if (room == null) return new ViewingSessionRoom(contentId);
            return room;
        });
    }

    @Override
    public void remove(ViewingSessionRoom room) {
        rooms.remove(room.contentId(),room);
    }

    @Override
    public Optional<ViewingSessionRoom> find(UUID contentId) {
        return Optional.ofNullable(rooms.get(contentId));
    }

}
