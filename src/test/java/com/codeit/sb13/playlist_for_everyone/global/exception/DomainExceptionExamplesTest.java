package com.codeit.sb13.playlist_for_everyone.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.sb13.playlist_for_everyone.global.exception.content.ContentNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.follow.FollowNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.notification.NotificationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.playlist.PlayListNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.review.ReviewNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

@DisplayName("도메인 예외 작성 예시의 오류 코드 매핑")
class DomainExceptionExamplesTest {

    @ParameterizedTest(name = "{0}: 두 생성자의 오류 코드 매핑을 확인한다")
    @MethodSource("notFoundExceptions")
    @DisplayName("예시 예외의 두 생성자는 도메인별 오류 코드와 404 상태 및 메시지를 사용한다")
    void mapsBothConstructorsToTheExpectedErrorCode(
            ApiErrorCode expectedCode,
            Function<Map<String, Object>, EveryOneException> withoutCause,
            BiFunction<Map<String, Object>, Throwable, EveryOneException> withCause
    ) {
        Map<String, Object> details = Map.of("id", "missing-id");
        Throwable cause = new IllegalStateException("원인 예외");

        EveryOneException exception = withoutCause.apply(details);
        EveryOneException causedException = withCause.apply(details, cause);

        for (EveryOneException actual : new EveryOneException[]{exception, causedException}) {
            assertThat(actual.getApiErrorCode()).isEqualTo(expectedCode);
            assertThat(actual.getApiErrorCode().getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(actual).hasMessage(expectedCode.getMessage());
        }
    }

    static Stream<Arguments> notFoundExceptions() {
        return Stream.of(
                exceptionCase(ApiErrorCode.USER_NOT_FOUND, UserNotFoundException::new, UserNotFoundException::new),
                exceptionCase(ApiErrorCode.CONTENT_NOT_FOUND, ContentNotFoundException::new, ContentNotFoundException::new),
                exceptionCase(ApiErrorCode.CONVERSATION_NOT_FOUND, ConversationNotFoundException::new, ConversationNotFoundException::new),
                exceptionCase(ApiErrorCode.FOLLOW_NOT_FOUND, FollowNotFoundException::new, FollowNotFoundException::new),
                exceptionCase(ApiErrorCode.NOTIFICATION_NOT_FOUND, NotificationNotFoundException::new, NotificationNotFoundException::new),
                exceptionCase(ApiErrorCode.PLAYLIST_NOT_FOUND, PlayListNotFoundException::new, PlayListNotFoundException::new),
                exceptionCase(ApiErrorCode.REVIEW_NOT_FOUND, ReviewNotFoundException::new, ReviewNotFoundException::new)
        );
    }

    private static Arguments exceptionCase(
            ApiErrorCode code,
            Function<Map<String, Object>, EveryOneException> withoutCause,
            BiFunction<Map<String, Object>, Throwable, EveryOneException> withCause
    ) {
        return Arguments.of(code, withoutCause, withCause);
    }
}
