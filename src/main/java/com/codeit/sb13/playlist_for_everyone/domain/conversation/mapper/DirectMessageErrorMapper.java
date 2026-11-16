package com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper;

import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import org.springframework.stereotype.Component;

@Component
public class DirectMessageErrorMapper {

    public ApiErrorResponse toErrorResponse(Throwable exception){
        return null;
    }
}
