package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.Watcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// todo - 부족한 필드가 채워지면, 매퍼 매서드도 마저 작성.
@Component
@RequiredArgsConstructor
public class WatchingSessionMapper {

    public Watcher toWatcher(User user) {
        if(user == null)
            return null;
        return new Watcher(
                user.getId(),
                user.getName(),
//                user.getProfileImageUrl()
                null    // 임시. Profile image 가 오면 조회하는 방식으로 변경.
        );
    }

    public WatchContent toWatchContent(ContentDto content) {
        if (content == null)
            return null;
        return new WatchContent(
                content.id(),
                content.type(),
                content.title(),
                content.description(),
                content.thumbnailUrl(),
                content.tags(),
                content.averageRating(),
                content.reviewCount()
        );
    }

}
