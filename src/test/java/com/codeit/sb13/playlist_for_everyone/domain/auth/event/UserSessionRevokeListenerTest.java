package com.codeit.sb13.playlist_for_everyone.domain.auth.event;

import com.codeit.sb13.playlist_for_everyone.domain.auth.event.UserSessionRevokedEvent.RevokeReason;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.UserSessionRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserLockedEvent;
import com.codeit.sb13.playlist_for_everyone.domain.user.event.UserRoleChangedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@DisplayName("권한 변경·계정 잠금 시 로그인 세션 종료")
class UserSessionRevokeListenerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SESSION_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserSessionRevokeListener listener;

    @Test
    @DisplayName("권한이 변경되면 로그인 세션을 종료하고 종료한 세션 ID로 이벤트를 발행한다")
    void 권한이_변경되면_세션을_종료한다() {
        given(userSessionRepository.revoke(USER_ID)).willReturn(Optional.of(SESSION_ID));

        listener.onRoleChanged(new UserRoleChangedEvent(USER_ID, Role.USER, Role.ADMIN));

        verify(userSessionRepository).revoke(USER_ID);
        verify(eventPublisher).publishEvent(
                new UserSessionRevokedEvent(USER_ID, SESSION_ID, RevokeReason.ROLE_CHANGED)
        );
    }

    @Test
    @DisplayName("계정이 잠기면 로그인 세션을 종료하고 종료한 세션 ID로 이벤트를 발행한다")
    void 계정이_잠기면_세션을_종료한다() {
        given(userSessionRepository.revoke(USER_ID)).willReturn(Optional.of(SESSION_ID));

        listener.onLocked(new UserLockedEvent(USER_ID));

        verify(userSessionRepository).revoke(USER_ID);
        verify(eventPublisher).publishEvent(
                new UserSessionRevokedEvent(USER_ID, SESSION_ID, RevokeReason.LOCKED)
        );
    }

    @Test
    @DisplayName("로그인 중이 아니면 종료 이벤트를 발행하지 않는다")
    void 로그인_중이_아니면_이벤트를_발행하지_않는다() {
        given(userSessionRepository.revoke(USER_ID)).willReturn(Optional.empty());

        listener.onLocked(new UserLockedEvent(USER_ID));

        verify(userSessionRepository).revoke(USER_ID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("세션 저장소 오류가 나도 이미 커밋된 관리자 요청을 실패시키지 않는다")
    void 세션_저장소_오류를_전파하지_않는다() {
        given(userSessionRepository.revoke(USER_ID))
                .willThrow(new RedisConnectionFailureException("테스트용 Redis 연결 실패"));

        // 권한·잠금은 필터가 요청마다 DB에서 확인하므로, 세션이 남아도 권한 판단은 바뀐 값으로 한다.
        assertThatCode(() -> listener.onLocked(new UserLockedEvent(USER_ID)))
                .doesNotThrowAnyException();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("세션 종료 후 이벤트 처리에 실패해도 예외를 전파하지 않는다")
    void 이벤트_처리_실패를_전파하지_않는다() {
        given(userSessionRepository.revoke(USER_ID)).willReturn(Optional.of(SESSION_ID));
        // 나중에 붙을 SSE·웹소켓 리스너가 실패한 상황이다. @EventListener는 발행 스레드에서 실행된다.
        willThrow(new IllegalStateException("테스트용 연결 종료 실패"))
                .given(eventPublisher).publishEvent(any(UserSessionRevokedEvent.class));

        assertThatCode(() -> listener.onLocked(new UserLockedEvent(USER_ID)))
                .doesNotThrowAnyException();

        // 이벤트 처리 실패와 관계없이 세션은 이미 종료되었다.
        verify(userSessionRepository).revoke(USER_ID);
    }

    @Test
    @DisplayName("세션 저장소의 예상하지 못한 오류도 전파하지 않는다")
    void 세션_저장소의_다른_오류도_전파하지_않는다() {
        // 저장된 값이 UUID 형식이 아닌 경우 등
        given(userSessionRepository.revoke(USER_ID))
                .willThrow(new IllegalArgumentException("테스트용 잘못된 세션 값"));

        assertThatCode(() -> listener.onRoleChanged(
                new UserRoleChangedEvent(USER_ID, Role.USER, Role.ADMIN)))
                .doesNotThrowAnyException();

        verifyNoInteractions(eventPublisher);
    }
}