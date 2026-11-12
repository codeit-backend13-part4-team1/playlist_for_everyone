package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

/**
 * 대화방 조회 쿼리의 별칭과 대응하는 내부 조회 결과이며 HTTP 응답 DTO와 구분한다.
 * Mapper가 요청자 기준의 상대방 정보와 메시지 응답으로 변환한다.
 */
public interface ConversationDetailProjection {
    Conversation getConversation();

    /** 메시지가 없으면 null이며, 최신 메시지는 생성 시각과 ID 순서로 결정한다. */
    DirectMessage getLastMessage();

    /** 최신 메시지의 읽음 여부가 아니라 요청자가 받은 미읽음 메시지의 존재 여부다. */
    boolean getHasUnread();
}
