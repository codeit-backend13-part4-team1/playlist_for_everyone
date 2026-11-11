package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    default Optional<ConversationDetailProjection> findDetailByUserPair(UUID userAId ,UUID userBId , UUID requesterId){
        throw new UnsupportedOperationException("대화방 상세 조회는 아직 구현되지 않았습니다.");
    }

}
