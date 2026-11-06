package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import org.hibernate.exception.ConstraintViolationException;

/**
 * 사용자 테이블의 DB 제약 조건 위반을 판별한다
 *
 * 판별 결과에 따른 예외 변환이나 로그 처리는 호출한 서비스가 결정한다.
 */
public class UserConstraintViolationDetector {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "UQ_USERS_EMAIL";

    private UserConstraintViolationDetector() {}

    public static boolean isEmailUniqueConstraintViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }

            cause = cause.getCause();
        }

        // 다른 제약 위반이나 판별할 수 없는 예외를 이메일 중복으로 처리하지 않는다.
        return false;
    }
}
