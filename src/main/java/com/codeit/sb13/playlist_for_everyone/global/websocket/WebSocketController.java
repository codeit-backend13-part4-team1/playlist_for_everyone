package com.codeit.sb13.playlist_for_everyone.global.websocket;


import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.ContentChatSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.chat.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.websocket.dto.event.WebSocketChatEvent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.payload.ChangeType;
import com.codeit.sb13.playlist_for_everyone.global.websocket.payload.WatchingSessionChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final ApplicationEventPublisher eventPublisher;

    // todo - 채널 참가, 떠나기 기능은 event 를 통한 메세지 publish 가 필요.
    //  때문에, 컨트롤러 단이 아닌, 채널 인터셉터를 통해 구현.
    //  본 controller 는 삭제 예정.
    @SubscribeMapping("/contents/{contentId}/watch")
    public WatchingSessionChange contentWatch(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal
    ){

//        SecurityUser sUser = (SecurityUser) principal;
//
//        // content 계통 구현 후 수정
//        WatchContent content = new WatchContent(
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null
//        );
//
//        // image profile 생성 후 수정
//        new Watcher(
//                sUser.getUserId(),
//                sUser.getUsername(),
//                null,    // user
//                content
//        );
//
//        // 추후 컨텐츠, 유저 서비스 이용해서 WatcherSessionDto 반환.
//        // 반환 객체는 WatcherSessionChange 이다.
//        new WatchingSessionDto(null,null,null,null);
        return new WatchingSessionChange(ChangeType.JOIN, null, 0L);
    }


    @MessageMapping("/contents/{contentId}/chat")
    public void contentChatReceive(
            @DestinationVariable UUID contentId,
            StompHeaderAccessor header,
            Principal principal,
            ContentChatSendRequest message
    ){

        SecurityUser sUser = (SecurityUser) principal;

        eventPublisher.publishEvent(new WebSocketChatEvent(
                contentId,
                new UserSummary(
                        sUser.getUserId(),
                        sUser.getUsername(),
                        null    // profile image url
                ),
                message.content()));
    }

}
