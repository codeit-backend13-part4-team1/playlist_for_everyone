package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;
import java.util.UUID;

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
