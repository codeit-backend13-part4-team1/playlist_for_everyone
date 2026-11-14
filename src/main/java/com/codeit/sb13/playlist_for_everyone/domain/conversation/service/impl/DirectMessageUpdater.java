package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.DirectMessageReadCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessageNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 누적 읽음 처리의 기준 메시지 검증과 벌크 갱신을 하나의 쓰기 트랜잭션에서 수행한다.
 *
 * <p>호출한 서비스가 대화방 참여 여부를 먼저 확인한다. 메서드 보안은 DirectMessageGuard를 사용하고,
 * 기준 메시지의 미존재·다른 대화방 소속 여부는 메서드 본문에서 검증하여 동일한 404 오류로 처리한다.
 * 트랜잭션은 REQUIRED이므로 상위 트랜잭션이 있으면 참여한다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageUpdater {

    private final DirectMessageRepository directMessageRepository;

    /**
     * 기준 메시지는 발신·수신 방향과 무관하게 허용하고, 실제 갱신은 요청자가 받은 미읽음 메시지로 제한한다.
     * 검증에 실패하면 갱신하지 않으며, 검증 성공 후 갱신 대상이 없으면 정상 종료한다.
     */
    @PreAuthorize("@directMessageGuard.canAccessDirectMessage("
            + "#command.directMessageId(), #command.conversationId(), #command.requesterId())")
    @Transactional
    public void read(DirectMessageReadCommand command){
        validateDirectMessageExists(command.directMessageId(), command.conversationId());

        // 벌크 갱신은 엔티티 감사 콜백에 의존하지 않으므로 이번 처리에 사용할 수정 시각을 직접 전달한다.
        directMessageRepository.updateReadStatusUpToMessage(
                command.conversationId(),
                command.requesterId(),
                command.directMessageId(),
                LocalDateTime.now());
    }

    /** 메시지 ID와 대화방 ID를 함께 확인하여 다른 대화방의 메시지도 같은 미존재 예외로 처리한다. */
    private void validateDirectMessageExists(UUID directMessageId, UUID conversationId) {
        boolean exists = directMessageRepository.existsByIdAndConversationId(
                directMessageId,
                conversationId
        );
        if (exists) return;

        throw new DirectMessageNotFoundException(Map.of(
                "conversationId", conversationId,
                "directMessageId", directMessageId
        ));

    }
}
