package com.codeit.sb13.playlist_for_everyone.domain.conversation.security;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DirectMessageGuard {

    private final DirectMessageRepository directMessageRepository;

    public boolean canAccessDirectMessage(UUID directMessageId, UUID conversationId, UUID userId){
        Optional<DirectMessage> foundDirectMessage = directMessageRepository.findById(directMessageId)
                .filter(dm -> dm.getConversation().getId().equals(conversationId));

        // 미존재·다른 대화방 메시지의 404 처리는 Updater의 검증에 위임한다.
        if (foundDirectMessage.isEmpty()) return true;

        return foundDirectMessage
                .filter(dm -> dm.isAccess(userId)).isPresent();
    }
}
