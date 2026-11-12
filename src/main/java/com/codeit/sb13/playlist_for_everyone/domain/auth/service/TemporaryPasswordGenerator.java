package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 비밀번호 초기화용 임시 비밀번호를 만든다
 * 메일을 보고 직접 입력하므로 모양이 비슷한 문자는 사용하지 않는다. (0, O, 1, l, I)
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 12;

    static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    static final String NUMBERS = "23456789";
    static final String SPECIALS = "!@#$%^&*";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        // RED: 다음 단계에서 구현
        return "";
    }



}
