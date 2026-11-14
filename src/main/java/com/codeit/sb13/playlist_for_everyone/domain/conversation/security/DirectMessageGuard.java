package com.codeit.sb13.playlist_for_everyone.domain.conversation.security;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 읽음 처리에서 기준 메시지의 발신자·수신자 접근을 확인하는 메서드 보안 Guard다.
 * 대화방 참여 검증은 서비스에서 선행하며, 메시지 미존재 처리에는 Updater의 후속 검증을 함께 사용한다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageGuard {

    private final DirectMessageRepository directMessageRepository;

    /**
     * 요청한 대화방에 속한 메시지라면 사용자 ID가 실제 발신자 또는 수신자인지 확인한다.
     *
     * <p>메시지가 없거나 대화방이 다를 때의 true는 검증 완료를 의미하지 않는다.
     * 보안 단계에서 접근 거절로 끝내지 않고 Updater의 소속·존재 검증으로 진행시켜 404를 반환하기 위한 것이다.
     * 이 결과만으로 메시지의 존재나 대화방 접근 권한을 판단해서는 안 된다.
     */
    public boolean canAccessDirectMessage(UUID directMessageId, UUID conversationId, UUID userId){
        Optional<DirectMessage> foundDirectMessage = directMessageRepository.findById(directMessageId)
                .filter(dm -> dm.getConversation().getId().equals(conversationId));

        if (foundDirectMessage.isEmpty()) return true;

        return foundDirectMessage
                .filter(dm -> dm.isAccess(userId)).isPresent();
    }
}
