package com.codeit.sb13.playlist_for_everyone.domain.auth.security.oauth;

import com.codeit.sb13.playlist_for_everyone.domain.auth.dto.SocialIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 제공자별 OAuth 로그인 결과를 공통 형태(SocialIdentity)로 바꾼다.
 */
@Component
public class SocialIdentityExtractor {

    public SocialIdentity extract(Authentication authentication) {
        throw new UnsupportedOperationException("미구현 상태");
    }

}
