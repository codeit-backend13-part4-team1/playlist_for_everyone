package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import com.codeit.sb13.playlist_for_everyone.domain.auth.repository.SocialAccountRepository;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;


    /**
     * 소셜 계정으로 로그인할 사용자를 찾고, 최초 로그인이면 가입시킨다.
     *
     * @return 로그인할 사용자 ID
     */
    @Transactional
    public UUID resolveUserId(SocialIdentity identity) {
        throw new UnsupportedOperationException("미구현");
    }

}
