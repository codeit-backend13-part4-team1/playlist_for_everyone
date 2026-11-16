package com.codeit.sb13.playlist_for_everyone.global.exception.conversation;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;

import java.util.Map;

/** 기준 메시지가 없거나 요청한 대화방에 속하지 않을 때 동일한 메시지 미존재 오류(404)를 표현한다. */
public class DirectMessageNotFoundException extends ConversationException {
    public DirectMessageNotFoundException(Map<String, Object> details) {
        super(ApiErrorCode.DIRECT_MESSAGE_NOT_FOUND, details);
    }
}
