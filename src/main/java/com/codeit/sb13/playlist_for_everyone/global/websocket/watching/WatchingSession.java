package com.codeit.sb13.playlist_for_everyone.global.websocket.watching;

import com.codeit.sb13.playlist_for_everyone.domain.auth.security.SecurityUser;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentDto;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketAuthInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketSessionNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.websocket.WebSocketDestinationInvalidException;
import com.codeit.sb13.playlist_for_everyone.global.service.MatchRule;
import com.codeit.sb13.playlist_for_everyone.global.websocket.SessionInfo;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.session.WebSocketRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.registry.subscription.SubscriptionRegistry;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.chat.ContentChatDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.*;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.WatchingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config.WatchingSessionProperties.*;
import static com.codeit.sb13.playlist_for_everyone.global.websocket.watching.WebSocketUtils.check;

/**
 * 동시 시청 도메인의 서비스를 큰 틀로 담당하는 클래스.
 * WatchingSessionService, UserService, ContentService 를 하나의 래퍼로 감싸서, 여청단위 클래스로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSession {

    private final MatchRule VIEW_DESTINATION_PATTERN = MatchRule
            .builder()
            .prefix(PATH_PREFIX)
            .pattern(UUID_REGEX)
            .postfix(VIEW_POSTFIX)
            .build();

    private final WatchingSessionMapper mapper;

    private final UserService userService;
    private final ContentService contentService;
    private final WatchingSessionService watchingSessionService;
    private final SubscriptionRegistry subscriptionRegistry;
    private final UserSummaryMapper userSummaryMapper;
    private final WebSocketRegistry webSocketRegistry;

    public String topic(SessionInfo info){
        return watchingSessionService.topic(info);
    }

    /**
     * 등록된 세션 정보의 수명을 갱신하는 매서드
     */
    public void refresh(SessionInfo info){
        Objects.requireNonNull(info);
        watchingSessionService.refresh(info.session());
    }

    /**
     * 세션 정보를 바탕으로, 동시 시청 채널에 참여(구독) 하고
     * 같은 경로에 대해 현재 채널 정보와, 참여한 시청자 정보 및, 시청중인 컨텐츠 정보를
     * WatchingSessionChange 로 반환해는 매서드.
     * @param info 채널 인터셉터에서 전달 된 프레임 정보.
     * @return WatchingSessionChange 세션에 일어난 변화 정보와, 세션 내부 정보를 담고있는 객체.
     */
    public WatchingSessionChange joinViewerSession(SessionInfo info){
        SessionInfo session = subscriptionRegistry.get(info)
                .orElseThrow(WebSocketSessionNotFoundException::new);
        SecurityUser auth = check(session.user());
        String contentId = getContentIdFromDestination(info);

        return getWatchingSessionChange(
                ChangeType.JOIN,
                auth,
                info,
                UUID.fromString(contentId)
        );
    }

    /**
     * 세션 정보를 바탕으로, 동시 시청 채널에 이탈(구독 취소) 하고
     * 같은 경로에 대해 현재 채널 정보와, 참여한 시청자 정보 및, 시청중인 컨텐츠 정보를
     * WatchingSessionChange 로 반환해는 매서드.
     * @param info 채널 인터셉터에서 전달 된 프레임 정보.
     * @return WatchingSessionChange 세션에 일어난 변화 정보와, 세션 내부 정보를 담고있는 객체.
     */
    public WatchingSessionChange leaveViewerSession(SessionInfo info){
        SessionInfo session = subscriptionRegistry.get(info)
                .orElseThrow(WebSocketSessionNotFoundException::new);
        SecurityUser auth = check(session.user());

        String contentId = getContentIdFromDestination(info);

        return getWatchingSessionChange(
                ChangeType.LEAVE,
                auth,
                info,
                UUID.fromString(contentId)
        );
    }

    /**
     * 세션 정보를 바탕으로 동시 시청 채팅 채널에 등록 한다.
     * @param info 세션 정보.
     */
    public void joinChatSession(SessionInfo info){
        check(info.destination());
        getContentIdFromDestination(info);

        SecurityUser auth = check(info.user());
        User user = userService.getById(auth.getUserId());
        if (user.isLocked()) throw new WebSocketAuthInvalidException();

        watchingSessionService.joinChatSession(info);
    }


    /**
     * 세션 정보를 바탕으로 동시 시청 채팅 채널에서 탈퇴 한다.
     * @param info 세션 정보.
     */
    public void leaveChatSession(SessionInfo info){
        check(info.destination());
        getContentIdFromDestination(info);

        watchingSessionService.leaveChatSession(info);
    }

    /**
     * 유저 id 와 채팅 내용을 바탕으로 채팅 메세지 객체를 조립한다.
     * @param info 세션 정보..
     * @param content 보내질 메세지.
     */
    public ContentChatDto getContentChatDto(SessionInfo info, String content){
        SecurityUser auth = check(info.user());

        User user = userService.getById(auth.getUserId());

        // 유저 락 정보 확인.
        if (user.isLocked()) throw new WebSocketAuthInvalidException();

        UserSummary userSummary = userSummaryMapper.toSummary(user);
        return new ContentChatDto(userSummary,content);
    }

    public void disconnectWithContentId(UUID contentId){
        List<String> sessionIds = watchingSessionService.disconnectWithContentDeleted(contentId);
        for (String sessionId : sessionIds)
            try {
                webSocketRegistry.close(sessionId);
            } catch (Exception e) {
                log.error("Failed to close websocket session: {}", sessionId, e);
            }
    }

    public void disconnectWithSessionIds(String sessionId){
        watchingSessionService.disconnectWithSessionIds(sessionId);
        try {
            webSocketRegistry.close(sessionId);
        } catch (Exception e) {
            log.error("Failed to close websocket session: {}", sessionId, e);
        }
    }




    private WatchingSessionChange getWatchingSessionChange(
            ChangeType type,
            SecurityUser auth,
            SessionInfo info,
            UUID contentId
    ){
        // 리소스 생성을 위한 조회
        User user = userService.getById(auth.getUserId());
        ContentDto content = contentService.getById(contentId);

        // 최신 사용자로 조회
        if(type == ChangeType.JOIN && user.isLocked()) // 사용자가 잠겨있다면, 로직 방어.
            throw new UserNotFoundException(Map.of());
        if(content == null)
            throw new ContentNotFoundException(Map.of());

        Watcher watcher = mapper.toWatcher(user);
        WatchContent watchContent = mapper.toWatchContent(content);
        WatchingSessionDto dto = new WatchingSessionDto(
                UUID.randomUUID(),  // todo - 전용 레포지토리 생성 후, 객체를 조립 할 때 id 와 함께 작성
                Instant.now(),  // todo - 전용 레포지토리 생성 후, 객체를 조립할 때 id 와 함께 작성
                watcher,
                watchContent
        );


        // 로직 마지막으로 컨텐츠에 시청자 등록.
        if (ChangeType.JOIN.equals(type))
            watchingSessionService.joinViewerSession(info);
        else
            watchingSessionService.leaveViewerSession(info);


        return new WatchingSessionChange(
                type,
                dto,
                watchingSessionService.getCurrentViewerCount(contentId)
        );
    }

    private String getContentIdFromDestination(SessionInfo info){
        try{
            String destination = check(info.destination());
            List<String> extract =
                    VIEW_DESTINATION_PATTERN.extract(destination);

            if (extract.isEmpty()) {
                log.error("[{}] id:{}(sub:{}, dst:{}) - 컨텐츠 ID 추출 실패. - pattern:{}",
                        this.getClass().getSimpleName(),
                        info.session(),
                        info.subscription(),
                        destination,
                        VIEW_DESTINATION_PATTERN
                );
                throw new NullPointerException();
            }

            return extract.get(0);
        } catch (NullPointerException e) {
            throw new WebSocketDestinationInvalidException(e);
        }
    }

}
