package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;

import java.util.UUID;

/**
 * 생성·단건·상대방 기준 조회와 목록의 각 항목에 공통으로 사용하는 대화방 응답이다.
 *
 * <p>lastestMessage 철자는 공식 API와 프론트엔드의 필드 계약을 유지한다.
 * 신규 대화방을 포함해 메시지가 없으면 lastestMessage는 null, hasUnread는 false이다.
 * 검색된 메시지와 관계없이 실제 최신 메시지를 담으며, with와 hasUnread는 요청자에 따라 달라진다.
 *
 * @param id 대화방 ID
 * @param with 요청자를 기준으로 한 상대방 정보
 * @param lastestMessage 최신 메시지. 메시지가 없는 대화방에서는 null
 * @param hasUnread 요청자가 받은 메시지 중 읽지 않은 메시지가 하나라도 있는지 여부
 */
public record ConversationDto(UUID id, UserSummary with, MessageDto lastestMessage, boolean hasUnread) {
}
