package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.entity.SocialAccount;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SocialAccountRepository;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth.SocialLoginError;
import com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth.SocialLoginException;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * 소셜 계정으로 로그인할 사용자를 결정한다.
 *
 * 이미 연결된 소셜 계정이면 연결된 사용자로 로그인하고, 처음이면 비밀번호 없이 가입시킨다.
 * 같은 이메일의 계정이 있어도 자동으로 연결하지 않는다.
 * 일반 가입에 이메일 인증이 없어, 타인이 먼저 가입한 계정에 연결되는 것을 막기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAccountService {

    // Kakao는 이메일을 제공하지 않아 회원 ID로 가상 이메일을 만든다.
    private static final String KAKAO_EMAIL_FORMAT = "kakao_%s@kakao.com";

    private static final int MAX_NAME_LENGTH = 100;

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;


    /**
     * 소셜 계정으로 로그인할 사용자를 찾고, 최초 로그인이면 가입시킨다.
     *
     * @return 로그인할 사용자 ID
     * @throws SocialLoginException 정책상 로그인을 거부하는 경우
     */
    @Transactional
    public UUID resolveUserId(SocialIdentity identity) {
        Objects.requireNonNull(identity, "소셜 사용자 정보는 필수입니다.");

        return socialAccountRepository
                .findByProviderAndProviderUserId(identity.provider() ,identity.providerUserId())
                .map(SocialAccount::getUser)
                .map(this::requireUnlocked)
                .orElseGet(()-> signUp(identity))
                .getId();
    }

    private User requireUnlocked(User user) {
        if (user.isLocked()) {
            throw new SocialLoginException(SocialLoginError.ACCOUNT_LOCKED);
        }
        return user;
    }

    private User signUp(SocialIdentity identity) {
        String email = resolveEmail(identity);

        if (userRepository.existsByEmail(email)) {
            throw new SocialLoginException(SocialLoginError.EMAIL_ALREADY_EXISTS);
        }

        try {
            // 바로 반영해 동시 가입의 중복 제약 위반을 이 메서드 안에서 확인한다.
            User user = userRepository.saveAndFlush(User.builder()
                    .email(email)
                    .name(resolveName(identity))
                    .password(null)
                    .role(Role.USER)
                    .build());

            socialAccountRepository.saveAndFlush(
                    SocialAccount.connect(user, identity.provider(), identity.providerUserId()));

            log.info("[AUTH] 소셜 가입 완료 userId - {}, provider - {}", user.getId(), identity.provider());
            return user;
        } catch (DataIntegrityViolationException exception) {
            // 이메일 확인과 저장 사이에 같은 계정이 먼저 저장된 경우다.
            throw new SocialLoginException(SocialLoginError.LOGIN_FAILED, exception);
        }
    }

    private String resolveEmail(SocialIdentity identity) {
        return switch (identity.provider()) {
            case GOOGLE -> {
                // Google이 소유를 확인한 이메일만 계정 이메일로 사용한다.
                if (!identity.emailVerified() || !StringUtils.hasText(identity.email())) {
                    throw new SocialLoginException(SocialLoginError.EMAIL_NOT_VERIFIED);
                }
                yield identity.email();
            }
            case KAKAO -> KAKAO_EMAIL_FORMAT.formatted(identity.providerUserId());
        };
    }

    private String resolveName(SocialIdentity identity) {
        if (StringUtils.hasText(identity.name())) {
            return truncateName(identity.name().strip());
        }
        return switch (identity.provider()) {
            case GOOGLE -> "Google 사용자";
            case KAKAO -> "카카오 사용자";
        };
    }

    /**
     * 제공자 이름이 컬럼 길이를 넘으면 가입이 계속 실패하므로 앞에서부터 잘라 저장한다.
     * 이모지처럼 char 2개로 이루어진 문자가 잘리지 않도록 코드 포인트 기준으로 자른다.
     */
    private String truncateName(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        int end = name.offsetByCodePoints(0, MAX_NAME_LENGTH);
        return name.substring(0, end).strip();
    }

}
