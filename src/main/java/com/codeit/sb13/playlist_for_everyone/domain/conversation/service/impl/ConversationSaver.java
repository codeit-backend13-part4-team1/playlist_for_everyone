package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 사용자 조회와 대화방 저장을 하나의 독립적인 트랜잭션으로 수행한다.
 *
 * <p>서비스와 별도 빈으로 두어 Spring 프록시를 거쳐 REQUIRES_NEW가 적용되도록 한다.
 * 상위 트랜잭션이 있으면 잠시 중단하고 새 저장 트랜잭션을 시작하므로,
 * 이후 상위 작업이 롤백되어도 여기서 커밋한 대화방은 유지된다.
 * 응답 DTO 변환과 프로필 이미지 URL 생성은 호출한 서비스에서 담당한다.
 */
@Component
@RequiredArgsConstructor
public class ConversationSaver {

    private final ConversationRepository conversationRepository;
    private final ConversationUserReader conversationUserReader;

    /**
     * 사용자 쌍을 정렬한 엔티티를 저장한다. 자기 대화 검증은 호출한 서비스에서 먼저 수행한다.
     *
     * <p>repository.save 직후가 아니라, 이 메서드를 감싼 트랜잭션 프록시가 커밋까지 마친 뒤
     * 호출자에게 정상 반환한다. 명시적인 flush 없이 커밋 시 발생한 UNIQUE 오류도
     * 호출한 서비스로 전달되므로, 서비스는 저장 트랜잭션 종료 후 재조회 여부를 결정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation save(ConversationCreateCommand command){
        User requester = conversationUserReader.getById(command.requesterId());
        User otherUser = getTargetUser(command.withUserId());
        return conversationRepository.save(new Conversation(new ConversationCommand(requester, otherUser)));
    }

    /**
     * 상대방의 미존재만 대화방 생성용 예외로 변환한다.
     * 요청자 미존재와 그 밖의 사용자 조회 오류는 원래 예외를 그대로 전달한다.
     */
    private User getTargetUser(UUID withUserId){
        try {
            return conversationUserReader.getById(withUserId);
        } catch (UserNotFoundException exception){
            throw new ConversationTargetNotFoundException(Map.of("withUserId", withUserId));
        }
    }


}
