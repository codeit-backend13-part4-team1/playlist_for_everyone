package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

/**
 * 저장 커밋 이후 DTO 변환 또는 실시간 발행에 실패했음을 나타낸다.
 * persisted=true는 DB 저장 완료를 뜻하며 브라우저 수신 성공을 뜻하지 않는다.
 * 클라이언트는 directMessageId와 conversationId로 기존 메시지를 조회할 수 있다.
 * 같은 내용을 새로 SEND하면 별도의 메시지가 생성되므로 자동 재전송의 근거로 사용하지 않는다.
 */
public class DirectMessagePublishException extends ConversationException {
    public DirectMessagePublishException(
            UUID directMessageId,
            UUID conversationId,
            Throwable cause
    ){
        super(ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED,
                Map.of(
                        "directMessageId", directMessageId,
                        "conversationId", conversationId,
                        "persisted",true
                ),
                cause);
    }
}
