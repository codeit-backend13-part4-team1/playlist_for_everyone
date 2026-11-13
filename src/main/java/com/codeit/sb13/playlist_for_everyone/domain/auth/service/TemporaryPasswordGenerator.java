package com.codeit.sb13.playlist_for_everyone.domain.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 비밀번호 초기화용 임시 비밀번호를 만든다
 * 메일을 보고 직접 입력하므로 모양이 비슷한 문자는 사용하지 않는다. (0, O, 1, l, I)
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 12;

    static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SPECIALS = "!@#$%^&*";

    static final String ALL = UPPER + LOWER + DIGITS + SPECIALS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);

        // 종류별로 최소 1개씩 넣어 규칙을 항상 만족시킨다.
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));

        // 나머지 자리는 전체 문자에서 고른다.
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }

        Collections.shuffle(chars, random);

        StringBuilder password = new StringBuilder(LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private char pick(String source) {
        return source.charAt(random.nextInt(source.length()));
    }



}
