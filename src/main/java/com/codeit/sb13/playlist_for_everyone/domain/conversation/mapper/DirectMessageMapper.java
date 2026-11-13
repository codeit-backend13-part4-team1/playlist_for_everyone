package com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper;


import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 조회된 메시지의 발신자·수신자 요약 정보를 구성하여 응답으로 변환한다.
 * UserSummaryMapper를 통해 프로필 이미지 URL도 생성하므로 필요한 연관관계가 조회되어 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class DirectMessageMapper {

    private final UserSummaryMapper userSummaryMapper;

    /** 메시지가 없으면 빈 메시지 객체 대신 null을 반환하여 대화방 응답 계약을 유지한다. */
    public MessageDto toMessageDto(DirectMessage message){
        if (message == null){
            return null;
        }
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                message.getCreatedAt(),
                userSummaryMapper.toSummary(message.getSender()),
                userSummaryMapper.toSummary(message.getReceiver()),
                message.getContent()
        );
    }
}
