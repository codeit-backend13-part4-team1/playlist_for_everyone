package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.ConversationService;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.DirectMessageService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 대화방 참여 검증 후 DM 조회·읽음 처리를 조정하고 조회 결과를 응답으로 변환한다.
 *
 * <p>직접 트랜잭션을 시작하지 않는다. 조회는 Reader의 읽기 전용 트랜잭션에,
 * 읽음 검증·갱신은 Updater의 쓰기 트랜잭션에 맡긴다. 인증 자체를 수행하지 않으므로
 * 호출자가 인증된 요청자 ID를 전달해야 한다.
 *
 * <p>상위 트랜잭션이 없는 일반 HTTP 호출에서는 Reader가 반환된 뒤 DTO와 프로필 URL을 만든다.
 * 상위 트랜잭션이 있으면 Reader가 참여하므로 응답 변환도 그 트랜잭션의 영향을 받는다.
 * 프로필 URL 생성 예외는 호출자에게 그대로 전달한다.
 */
@Service
@RequiredArgsConstructor
public class DirectMessageServiceImpl implements DirectMessageService {

    private final DirectMessageReader directMessageReader;
    private final DirectMessageMapper directMessageMapper;
    private final DirectMessageUpdater directMessageUpdater;
    private final ConversationService conversationService;

    /** 대화방 미존재·비참여를 먼저 거절한 뒤 Updater의 보안 검사와 기준 메시지 검증·갱신으로 진행한다. */
    @Override
    public void read(DirectMessageReadCommand command) {
        conversationService.validateParticipant(command.conversationId(), command.requesterId());

        directMessageUpdater.read(command);
    }

    /** 참여 검증, 목록·전체 개수 조회, 페이지 절삭, 커서 생성, 응답 변환 순서로 진행한다. */
    @Override
    public CursorResponse<MessageDto> search(DirectMessageSearchCondition condition) {
        conversationService.validateParticipant(condition.conversationId(), condition.requesterId());

        List<DirectMessage> result = directMessageReader.search(condition);
        long totalCount = directMessageReader.searchCount(condition);

        // Repository가 한 건 더 조회한 결과로 다음 페이지를 판단하고 초과 항목은 응답에서 제외한다.
        boolean hasNext = result.size() > condition.limit();
        List<DirectMessage> page = hasNext ? result.subList(0, condition.limit()) : result;

        String cursor = null;
        UUID idAfter = null;
        if (hasNext){
            // 초과 항목이 아니라 마지막 반환 항목을 기준으로 해야 다음 페이지에서 메시지가 누락되지 않는다.
            DirectMessage last = page.get(page.size() - 1);
            cursor = last.getCreatedAt().toString();
            idAfter = last.getId();
        }
        // 필요한 사용자·프로필은 조회 시 함께 로딩했으며, 실제 발신자·수신자 방향을 유지해 변환한다.
        List<MessageDto> dtos = page.stream()
                .map(directMessageMapper::toMessageDto)
                .toList();

        return new CursorResponse<>(
                dtos,
                cursor,
                idAfter,
                hasNext,
                totalCount,
                condition.sortBy().getSortKey(),
                condition.sortDirection()
        );
    }
}
