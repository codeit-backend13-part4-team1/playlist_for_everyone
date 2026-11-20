package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialAccount;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialProvider;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SocialAccountRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth.SocialLoginError;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth.SocialLoginException;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("소셜 계정 로그인 사용자 결정")
class SocialAccountServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SocialAccountService socialAccountService;

    @Test
    @DisplayName("이미 연결된 소셜 계정이면 연결된 사용자 ID를 반환한다.")
    void 이미_연결된_소셜_계정이면_연결된_사용자ID를_반환한다() {
        User user = userWithId("test@gmail.com");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .willReturn(Optional.of(SocialAccount.connect(user, SocialProvider.GOOGLE, "google-sub")));

        UUID result = socialAccountService.resolveUserId(google("test@gmail.com", "테스트", true));

        assertThat(result).isEqualTo(USER_ID);
        verify(userRepository, never()).saveAndFlush(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("연결된 사용자가 잠겨 있으면 로그인을 거부한다.")
    void 연결된_사용자가_잠겨_있으면_로그인을_거부한다() {
        User user = userWithId("test@gmail.com");
        user.lock();
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .willReturn(Optional.of(SocialAccount.connect(user, SocialProvider.GOOGLE, "google-sub")));

        assertError(google("test@gmail.com", "테스트", true), SocialLoginError.ACCOUNT_LOCKED);
    }

    @Test
    @DisplayName("Google 최초 로그인이면 검증된 이메일로 가입하고 소셜 계정을 연결한다.")
    void Google_최초_로그인이면_검증된_이메일로_가입하고_소셜_계정을_연결한다() {
        givenNotConnected(SocialProvider.GOOGLE, "google-sub");
        given(userRepository.existsByEmail("test@gmail.com")).willReturn(false);
        givenUserSaved();

        UUID result = socialAccountService.resolveUserId(google("test@gmail.com", "테스트", true));

        User savedUser = captureSavedUser();
        assertThat(result).isEqualTo(USER_ID);
        assertThat(savedUser.getEmail()).isEqualTo("test@gmail.com");
        assertThat(savedUser.getName()).isEqualTo("테스트");
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);

        SocialAccount savedAccount = captureSavedAccount();
        assertThat(savedAccount.getUser()).isSameAs(savedUser);
        assertThat(savedAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(savedAccount.getProviderUserId()).isEqualTo("google-sub");
    }

    @ParameterizedTest(name = "email={0}, verified={1}")
    @CsvSource(value = {
            "test@gmail.com, false",
            "NULL, true",
            "'', true"
    }, nullValues = "NULL")
    @DisplayName("Google 이메일이 없거나 검증되지 않았으면 가입하지 않는다.")
    void Google_이메일이_없거나_검증되지_않았으면_가입하지_않는다(String email, boolean verified) {
        givenNotConnected(SocialProvider.GOOGLE, "google-sub");

        assertError(google(email, "테스트", verified), SocialLoginError.EMAIL_NOT_VERIFIED);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 이메일의 계정이 이미 있으면 연결하지 않고 거부한다.")
    void 같은_이메일의_계정이_이미_있으면_연결하지_않고_거부한다() {
        givenNotConnected(SocialProvider.GOOGLE, "google-sub");
        given(userRepository.existsByEmail("test@gmail.com")).willReturn(true);

        assertError(google("test@gmail.com", "테스트", true), SocialLoginError.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Kakao 최초 로그인이면 회원 ID로 만든 가상 이메일로 가입한다.")
    void Kakao_최초_로그인이면_회원ID로_만든_가상_이메일로_가입한다() {
        givenNotConnected(SocialProvider.KAKAO, "38457");
        given(userRepository.existsByEmail("kakao_38457@kakao.com")).willReturn(false);
        givenUserSaved();

        socialAccountService.resolveUserId(
                new SocialIdentity(SocialProvider.KAKAO, "38457", null, "테스트", false));

        User savedUser = captureSavedUser();
        assertThat(savedUser.getEmail()).isEqualTo("kakao_38457@kakao.com");
        assertThat(savedUser.getName()).isEqualTo("테스트");
        assertThat(savedUser.getPassword()).isNull();
    }

    @ParameterizedTest(name = "{0} → {2}")
    @CsvSource({
            "GOOGLE, test@gmail.com, Google 사용자",
            "KAKAO, , 카카오 사용자"
    })
    @DisplayName("이름이 없으면 제공자별 기본 이름으로 가입한다.")
    void 이름이_없으면_제공자별_기본_이름으로_가입한다(SocialProvider provider, String email, String expectedName) {
        givenNotConnected(provider, "provider-id");
        given(userRepository.existsByEmail(any())).willReturn(false);
        givenUserSaved();

        socialAccountService.resolveUserId(new SocialIdentity(provider, "provider-id", email, " ", true));

        assertThat(captureSavedUser().getName()).isEqualTo(expectedName);
    }

    @Test
    @DisplayName("동시 최초 로그인으로 중복 제약에 걸리면 로그인 실패로 처리한다.")
    void 동시_최초_로그인으로_중복_제약에_걸리면_로그인_실패로_처리한다() {
        givenNotConnected(SocialProvider.GOOGLE, "google-sub");
        given(userRepository.existsByEmail("test@gmail.com")).willReturn(false);
        given(userRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertError(google("test@gmail.com", "테스트", true), SocialLoginError.LOGIN_FAILED);
    }

    private SocialIdentity google(String email, String name, boolean verified) {
        return new SocialIdentity(SocialProvider.GOOGLE, "google-sub", email, name, verified);
    }

    private void givenNotConnected(SocialProvider provider, String providerUserId) {
        given(socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId))
                .willReturn(Optional.empty());
    }

    // DB가 ID를 부여하는 것처럼 저장한 사용자에게 ID를 넣어 돌려준다.
    private void givenUserSaved() {
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", USER_ID);
            return user;
        });
    }

    private User captureSavedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private SocialAccount captureSavedAccount() {
        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void assertError(SocialIdentity identity, SocialLoginError expected) {
        assertThatThrownBy(() -> socialAccountService.resolveUserId(identity))
                .isInstanceOf(SocialLoginException.class)
                .extracting(e -> ((SocialLoginException) e).getError())
                .isEqualTo(expected);
    }

    private User userWithId(String email) {
        User user = User.builder()
                .email(email)
                .name("기존 사용자")
                .password(null)
                .role(Role.USER)
                .build();

        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

}
