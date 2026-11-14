package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;

import java.util.UUID;

/**
 * 인증된 요청자를 기준으로 1:1 대화방 생성과 목록·단건·상대방 기준 조회를 제공한다.
 *
 * <p>호출자는 인증된 요청자 ID와 입력 검증을 마친 조건을 전달한다.
 * 응답의 상대방·미읽음 정보는 요청자 기준이며, 조회 메서드는 대화방 생성이나 읽음 변경을 수행하지 않는다.
 */
public interface ConversationService {

    /**
     * 같은 사용자 쌍은 요청 방향과 관계없이 동일한 대화방을 사용한다.
     * 기존 대화방은 저장된 최신 메시지와 요청자 기준의 미읽음 여부를 함께 반환한다.
     *
     * @param createCommand 인증된 요청자 ID와 필수 값 검증을 통과한 상대방 ID
     * @return 요청자를 기준으로 상대방 정보를 구성한 대화방 응답
     */
    ConversationDto create(ConversationCreateCommand createCommand);

    /**
     * 요청자와 상대방 사이에 이미 존재하는 대화방을 반환한다.
     *
     * <p>자기 자신을 상대방으로 지정하면 자기 대화 예외로 거절한다.
     * 상대 사용자 또는 대화방이 없으면 대화방 미존재 예외를 전달하며 새 대화방을 생성하지 않는다.
     *
     * @param condition 인증된 요청자 ID와 필수 값 검증을 통과한 상대방 ID
     * @return 두 사용자의 요청 방향과 무관하게 같은 대화방을 요청자 기준으로 변환한 응답
     */
    ConversationDto findWithUser(ConversationFindCondition condition);

    /**
     * 요청자가 참여한 대화방을 ID로 조회한다.
     * 존재하지 않는 경우와 참여자가 아닌 경우 모두 같은 대화방 미존재 예외를 전달한다.
     *
     * @param condition 인증된 요청자 ID와 조회할 대화방 ID
     * @return 상대방 정보·최신 메시지·요청자 기준 미읽음 여부를 담은 응답
     */
    ConversationDto findById(ConversationFindByIdCondition condition);

    /**
     * 본인 대화방에서 상대방 이름 또는 전체 메시지 본문을 검색하여 커서 페이지로 반환한다.
     *
     * <p>대화방 생성 시각과 ID를 같은 방향으로 정렬한다. totalCount에는 커서 조건을 적용하지 않으며,
     * 마지막 페이지의 두 커서는 null이다. 검색에 일치한 메시지와 별개로 실제 최신 메시지를 반환한다.
     *
     * @param condition 필수 값·양수 limit·정렬값·커서 형식과 조합 검증을 마친 조회 조건
     * @return 대화방 목록, 다음 페이지 정보, 전체 검색 개수와 적용한 정렬 조건
     */
    CursorResponse<ConversationDto> search(ConversationSearchCondition condition);

    void validateParticipant(UUID conversationId, UUID requesterId);
}
