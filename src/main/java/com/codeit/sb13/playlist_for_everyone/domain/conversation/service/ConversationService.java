package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;

import java.util.UUID;

/**
 * 인증된 사용자와 상대방 사이의 1:1 대화방을 생성하거나 기존 대화방을 반환한다.
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

    ConversationDto findWithUser(ConversationFindCondition condition);

    ConversationDto findById(ConversationFindByIdCondition condition);

    CursorResponse<ConversationDto> search(ConversationSearchCondition condition);
}
