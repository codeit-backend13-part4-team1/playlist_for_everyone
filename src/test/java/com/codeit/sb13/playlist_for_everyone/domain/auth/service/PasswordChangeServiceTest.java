package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPassword;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.TemporaryPasswordStatus;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.TemporaryPasswordRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.user.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("비밀번호 변경")
class PasswordChangeServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String NEW_PASSWORD = "new-Password";
    private static final String ENCODED_PASSWORD = "encoded-new-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TemporaryPasswordRepository temporaryPasswordRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordChangeService passwordChangeService;


    @Test
    @DisplayName("새 비밀번호를 암호화해서 저장한다.")
    void 새_비밀번호를_암호화해서_저장한다() {
        User user = givenUser();
        given(passwordEncoder.encode(NEW_PASSWORD)).willReturn(ENCODED_PASSWORD);

        passwordChangeService.changePassword(USER_ID, NEW_PASSWORD);

        assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("사용 중인 임시 비밀번호를 모두 파기한다.")
    void 사용_중인_임시_비밀번호를_모두_파기한다() {
        givenUser();
        given(passwordEncoder.encode(NEW_PASSWORD)).willReturn(ENCODED_PASSWORD);
        TemporaryPassword active =
                TemporaryPassword.issue(USER_ID, "encoded-temporary", LocalDateTime.now().plusMinutes(3));
        given(temporaryPasswordRepository.findAllByUserIdAndStatus(USER_ID, TemporaryPasswordStatus.ACTIVE))
                .willReturn(List.of(active));

        passwordChangeService.changePassword(USER_ID, NEW_PASSWORD);

        assertThat(active.getStatus()).isEqualTo(TemporaryPasswordStatus.DISCARDED);
    }

    @Test
    @DisplayName("사용자가 없으면 예외가 발생하고 아무것도 변경하지 않는다.")
    void 사용자가_없으면_예외가_발생한다() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordChangeService.changePassword(USER_ID, NEW_PASSWORD))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(passwordEncoder, temporaryPasswordRepository);
    }

    private User givenUser() {
        User user = User.builder()
                .email("user@test.com")
                .name("사용자")
                .password("encoded-old-password")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        return user;
    }

}
