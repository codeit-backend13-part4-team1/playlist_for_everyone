package com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DM 메시지 전송 요청 검증")
class DirectMessageSendRequestTest {

    @ParameterizedTest(name = "content=[{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("본문이 null이거나 빈 문자열 또는 공백 문자뿐이면 검증에 실패한다.")
    void rejectsBlankContent(String content){
        DirectMessageSendRequest request = new DirectMessageSendRequest(content);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()){
            Set<ConstraintViolation<DirectMessageSendRequest>> violations = factory.getValidator().validate(request);
            assertThat(violations).extracting(violation ->
                    violation.getPropertyPath().toString())
                    .containsExactly("content");
        }
    }

    @Test
    @DisplayName("공백이 아닌 본문이 있으면 검증에 통과한다.")
    void acceptsNonBlankContent(){
        DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요.\n같이 이야기해요.");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()){
            Set<ConstraintViolation<DirectMessageSendRequest>> violations = factory.getValidator().validate(request);
            assertThat(violations).isEmpty();
        }
    }


}