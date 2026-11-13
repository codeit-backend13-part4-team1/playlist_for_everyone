package com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 대화방 엔티티 또는 조회 결과를 요청자 기준의 응답으로 변환한다.
 *
 * <p>사용자 요약을 만들면서 프로필 이미지 URL을 생성하므로 관련 예외가 발생할 수 있다.
 * 호출자는 사용자·프로필 이미지 등 변환에 필요한 연관관계를 미리 조회해야 한다.
 * 대화방 생성 흐름에서는 Saver의 저장 커밋이 끝난 뒤 이 변환을 호출한다.
 */
@Component
@RequiredArgsConstructor
public class ConversationMapper {

    private final UserSummaryMapper userSummaryMapper;
    private final DirectMessageMapper messageMapper;

    /**
     * 새로 저장한 대화방을 메시지 없음·미읽음 없음의 초기 상태로 변환한다.
     * 기존 대화방의 실제 메시지 상태가 필요하면 Projection을 받는 변환을 사용한다.
     */
    public ConversationDto toConversationDto(UUID requesterId, Conversation conversation) {
        return toConversationDto(
                conversation,
                false,
                null,
                requesterId
        );
    }

    /**
     * 조회된 최신 메시지와 요청자별 미읽음 여부를 초기값으로 덮어쓰지 않고 응답에 반영한다.
     */
    public ConversationDto toConversationDto(UUID requesterId, ConversationDetailProjection projection) {
        return toConversationDto(
                projection.getConversation(),
                projection.getHasUnread(),
                projection.getLastMessage(),
                requesterId
        );
    }

    private ConversationDto toConversationDto(Conversation conversation, boolean hasUnread, DirectMessage lastMessage, UUID requesterId) {
        return new ConversationDto(
                conversation.getId(),
                toOtherUserSummary(conversation, requesterId),
                messageMapper.toMessageDto(lastMessage),
                hasUnread
        );
    }

    private UserSummary toOtherUserSummary(Conversation conversation, UUID requesterId) {
        // 요청자가 두 참여자 중 한 명이라는 전제다. 저장 위치 A·B가 아닌 요청자 ID로 상대방을 고른다.
        if (conversation.getUserAId().equals(requesterId)) {
            return userSummaryMapper.toSummary(conversation.getUserB());
        }
        return userSummaryMapper.toSummary(conversation.getUserA());
    }

}
