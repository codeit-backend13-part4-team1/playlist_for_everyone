package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;

/**
 * 대화방 엔티티·최신 메시지 엔티티·요청자 기준 미읽음 여부를 전달하는 내부 조회 결과 계약이다.
 *
 * <p>Querydsl 생성자 조회로 만든 ConversationDetailResult가 이 계약을 구현한다.
 * HTTP 응답 DTO가 아닌 엔티티 참조를 담은 결과이며, Mapper가 상대방 정보와 메시지 응답으로 변환한다.
 */
public interface ConversationDetailProjection {
    Conversation getConversation();

    /** 메시지가 없으면 null이며, 최신 메시지는 생성 시각 내림차순·동일 시각의 ID 내림차순으로 결정한다. */
    DirectMessage getLastMessage();

    /** 최신 메시지의 읽음 여부가 아니라 요청자가 받은 미읽음 메시지의 존재 여부다. */
    boolean getHasUnread();
}
