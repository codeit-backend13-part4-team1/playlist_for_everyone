package com.codeit.sb13.playlist_for_everyone.global.websocket.error;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.controller.DirectMessageWebSocketController;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.request.DirectMessageSendRequest;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.ApiErrorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationTargetNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessagePublishException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WebSocketErrorMapper.class)
@DisplayName("WebSocket 오류 응답 변환")
class WebSocketErrorMapperTest {

    @Autowired
    private WebSocketErrorMapper errorMapper;
    private final UUID directMessageId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    @DisplayName("DM 전송 실패 예외를 변환하면 저장된 메시지 정보와 저장 완료 여부를 유지한다.")
    void toErrorResponse_preservesPersistedMessageDetails(){
        DirectMessagePublishException exception = new DirectMessagePublishException(directMessageId, conversationId, new IllegalStateException("Redis 발행 실패"));

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(DirectMessagePublishException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo(ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED.getMessage());
        assertThat(response.details())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "directMessageId", directMessageId,
                        "conversationId", conversationId,
                        "persisted", true
                ));
    }

    @Test
    @DisplayName("DM 전송 실패 예외가 메시징 예외로 감싸져도 저장된 메시지 정보를 유지한다.")
    void toErrorResponse_preservesPersistedMessageDetailsWhenWrapped(){
        DirectMessagePublishException cause = new DirectMessagePublishException(directMessageId, conversationId, new IllegalStateException("Redis 발행 실패"));
        MessageDeliveryException exception = new MessageDeliveryException(new GenericMessage<>("DM 전송 요청"), cause);

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);
        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(DirectMessagePublishException.class.getSimpleName());
        assertThat(response.message()).isEqualTo(ApiErrorCode.DIRECT_MESSAGE_PUBLISH_FAILED.getMessage());
        assertThat(response.details())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                "directMessageId", directMessageId,
                                "conversationId", conversationId,
                                "persisted", true
                        ));
    }

    @Test
    @DisplayName("인증 실패 예외는 내부 원인을 노출하지 않고 공통 인증 오류 메시지로 변환한다.")
    void toErrorResponse_hidesAuthenticationFailureDetails(){
        BadCredentialsException exception = new BadCredentialsException("토큰 서명 검증 실패: 내부 상세 원인");
        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(BadCredentialsException.class.getSimpleName());
        assertThat(response.message()).isEqualTo("유효하지 않은 인증 정보입니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("접근 거부 예외는 내부 원인을 노출하지 않고 공통 권한 오류 메시지로 변환한다.")
    void toErrorResponse_hidesAccessDeniedDetails(){
        AccessDeniedException exception = new AccessDeniedException("대화방 참여자 검증 실패: 내부 상세 원인");
        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(AccessDeniedException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo("접근 권한이 없습니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("요청 메시지 변환 실패는 내부 원인을 노출하지 않고 잘못된 요청 응답으로 변환한다.")
    void toErrorResponse_mapsMessageConversionFailureToInvalidRequest(){
        MessageConversionException exception = new MessageConversionException("JSON 변환 실패: 내부 상세 원인");
        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(MessageConversionException.class.getSimpleName());
        assertThat(response.message()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("메시지 입력 검증에 실패하면 잘못된 요청 응답에 필드별 오류 정보를 포함한다.")
    void toErrorResponse_preservesValidationFieldErrors() throws NoSuchMethodException {
        DirectMessageSendRequest request = new DirectMessageSendRequest("   ");
        String validationMessage = "메시지 내용은 비어 있을 수 없습니다.";

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError("request", "content", validationMessage));
        MethodParameter parameter = new MethodParameter(DirectMessageWebSocketController.class.getDeclaredMethod(
                "send",
                UUID.class,
                DirectMessageSendRequest.class,
                Authentication.class
        ), 1);

        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(new GenericMessage<>(request), parameter, bindingResult);

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(MethodArgumentNotValidException.class.getSimpleName());
        assertThat(response.message()).isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(response.details()).containsExactlyInAnyOrderEntriesOf(Map.of("content", List.of(validationMessage)));
    }

    @Test
    @DisplayName("DB 저장 실패는 내부 원인을 노출하지 않고 공통 서버 오류 응답으로 변환한다.")
    void toErrorResponse_hidesDatabaseFailureDetails(){
        DataIntegrityViolationException exception = new DataIntegrityViolationException("DM 저장 실패: 내부 SQL 및 제약조건 정보");

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);
        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(DataIntegrityViolationException.class.getSimpleName());
        assertThat(response.message()).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("업무 예외 안에 다른 업무 예외가 있어도 바깥 예외의 메시지와 상세정보를 유지한다.")
    void toErrorResponse_preservesOuterDomainException(){
        UUID requesterId = UUID.randomUUID();

        ConversationTargetNotFoundException cause = new ConversationTargetNotFoundException(Map.of("userId", requesterId));
        ConversationNotFoundException exception = new ConversationNotFoundException(Map.of("conversationId", conversationId), cause);

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(ConversationNotFoundException.class.getSimpleName());
        assertThat(response.message()).isEqualTo(ApiErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        assertThat(response.details()).containsExactlyInAnyOrderEntriesOf(Map.of("conversationId", conversationId));
    }

    @Test
    @DisplayName("접근 거부 예외가 메시징 예외로 감싸져도 공통 권한 오류 응답으로 변환한다.")
    void toErrorResponse_preservesAccessDeniedResponseWhenWrapped(){
        AccessDeniedException cause = new AccessDeniedException("대화방 참여자 검증 실패");
        MessageDeliveryException exception = new MessageDeliveryException(new GenericMessage<>("DM 전송 요청"), cause);

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName()).isEqualTo(AccessDeniedException.class.getSimpleName());
        assertThat(response.message()).isEqualTo("접근 권한이 없습니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("인증 실패 예외가 메시징 예외로 감싸져도 공통 인증 오류 응답으로 변환한다.")
    void toErrorResponse_preservesAuthenticationFailureResponseWhenWrapped() {
        BadCredentialsException cause =
                new BadCredentialsException("Bearer 토큰이 비어 있습니다.");
        MessageDeliveryException exception = new MessageDeliveryException(
                new GenericMessage<>("CONNECT 요청"),
                cause
        );

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(BadCredentialsException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo("유효하지 않은 인증 정보입니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("잠긴 계정 예외는 내부 원인을 노출하지 않고 공통 인증 오류 응답으로 변환한다.")
    void toErrorResponse_mapsLockedAccountToAuthenticationFailure() {
        LockedException exception = new LockedException("잠긴 계정입니다.");

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(LockedException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo("유효하지 않은 인증 정보입니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("JWT 검증 실패는 토큰 상세 정보를 노출하지 않고 공통 인증 오류 응답으로 변환한다.")
    void toErrorResponse_mapsJwtFailureToAuthenticationFailure() {
        JwtException exception = new JwtException("JWT 검증 실패: 내부 토큰 상세 정보");

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(JwtException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo("유효하지 않은 인증 정보입니다.");
        assertThat(response.details()).isEmpty();
    }

    @Test
    @DisplayName("빈 메시지 본문의 검증 오류는 잘못된 요청 응답에 객체별 오류 정보를 포함한다.")
    void toErrorResponse_preservesValidationGlobalErrors() throws NoSuchMethodException {
        byte[] payload = new byte[0];
        String validationMessage = "Payload value must not be empty";
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(payload, "request");
        bindingResult.addError(new ObjectError("request", validationMessage));

        MethodParameter parameter = new MethodParameter(
                DirectMessageWebSocketController.class.getDeclaredMethod(
                        "send",
                        UUID.class,
                        DirectMessageSendRequest.class,
                        Authentication.class
                ),
                1
        );
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new GenericMessage<>(payload),
                parameter,
                bindingResult
        );

        ApiErrorResponse response = errorMapper.toErrorResponse(exception);

        assertThat(response).isNotNull();
        assertThat(response.exceptionName())
                .isEqualTo(MethodArgumentNotValidException.class.getSimpleName());
        assertThat(response.message())
                .isEqualTo(ApiErrorCode.INVALID_REQUEST.getMessage());
        assertThat(response.details())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("request", List.of(validationMessage))
                );
    }
}
