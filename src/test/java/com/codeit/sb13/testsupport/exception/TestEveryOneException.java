package com.codeit.sb13.testsupport.exception;

import com.codeit.sb13.playlist_for_everyone.global.exception.ApiErrorCode;
import com.codeit.sb13.playlist_for_everyone.global.exception.EveryOneException;
import java.util.Map;

// 공통 예외 동작을 도메인별 예외 구현과 독립적으로 검증하기 위한 테스트용 예외이다.
public final class TestEveryOneException extends EveryOneException {

    public TestEveryOneException(ApiErrorCode apiErrorCode, Map<String, Object> details) {
        super(apiErrorCode, details);
    }

    public TestEveryOneException(ApiErrorCode apiErrorCode, Map<String, Object> details, Throwable cause) {
        super(apiErrorCode, details, cause);
    }
}
