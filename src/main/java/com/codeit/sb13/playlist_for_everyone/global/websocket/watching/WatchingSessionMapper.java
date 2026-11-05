package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentTag;
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
            return new Watcher(null, null, null);
        return new Watcher(
                user.getId(),
                user.getName(),
//                user.getProfileImageUrl()
                null    // 임시. Profile image 가 오면 조회하는 방식으로 변경.
        );
    }

    public WatchContent toWatchContent(Content content) {
        if (content == null)
            return new WatchContent(null, null, null, null,
                    null, null, 0.0, 0);
        return new WatchContent(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getTags().stream().map(ContentTag::getTag).toList(),
                0.0, // 임시. 추후 컨텐츠 도메인에서 평균 평점이 조회되는 매서드 사용.
                0 // 임시. 리뷰어 카운터.
        );
    }

}
