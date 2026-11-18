package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("임시 비밀번호")
class TemporaryPasswordTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.of(2026, 11, 11, 18, 3);


    @Test
    @DisplayName("발급하면 사용 가능한 상태이다.")
    void 발급하면_사용_가능한_상태다() {
        TemporaryPassword temporaryPassword = TemporaryPassword.issue(USER_ID, "encoded", EXPIRED_AT);

        assertThat(temporaryPassword.getStatus()).isEqualTo(TemporaryPasswordStatus.ACTIVE);
    }

    @Test
    @DisplayName("만료 시각 전이면 사용할 수 있다.")
    void 만료_시각_전이면_사용할_수_있다() {
        TemporaryPassword temporaryPassword = TemporaryPassword.issue(USER_ID, "encoded", EXPIRED_AT);

        assertThat(temporaryPassword.isUsableAt(EXPIRED_AT.minusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("만료 시각부터는 사용할 수 없다.")
    void 만료_시각부터는_사용할_수_없다() {
        TemporaryPassword temporaryPassword = TemporaryPassword.issue(USER_ID, "encoded", EXPIRED_AT);

        assertThat(temporaryPassword.isUsableAt(EXPIRED_AT)).isFalse();
        assertThat(temporaryPassword.isUsableAt(EXPIRED_AT.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("파기하면 만료 전이어도 사용할 수 없다.")
    void 파기하면_만료_전이어도_사용할_수_없다() {
        TemporaryPassword temporaryPassword = TemporaryPassword.issue(USER_ID, "encoded", EXPIRED_AT);

        temporaryPassword.discard();

        assertThat(temporaryPassword.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
        assertThat(temporaryPassword.isUsableAt(EXPIRED_AT.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("필수 값이 없으면 발급할 수 없다.")
    void 필수_값이_없으면_발급할_수_없다() {
        assertThatThrownBy(() -> TemporaryPassword.issue(null, "encoded", EXPIRED_AT))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(()-> TemporaryPassword.issue(USER_ID, null, EXPIRED_AT))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(()-> TemporaryPassword.issue(USER_ID, "encoded", null))
                .isInstanceOf(NullPointerException.class);
    }

}
