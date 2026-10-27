package com.codeit.sb13.playlist_for_everyone.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ApiErrorCode {

    //USER U
    USER_NOT_FOUND("U_001", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_EMAIL_DUPLICATED("U_002", HttpStatus.BAD_REQUEST, "이미 사용중인 이메일입니다."),

    //CONTENT C
    CONTENT_NOT_FOUND("C_001", HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다."),

    //CONVERSATION CS
    CONVERSATION_NOT_FOUND("CS_001", HttpStatus.NOT_FOUND, "대화방을 찾을 수 없습니다."),

    //FOLLOW F
    FOLLOW_NOT_FOUND("F_001", HttpStatus.NOT_FOUND, "팔로우를 찾을 수 없습니다."),
    FOLLOW_CREATION_FAILED("F_002", HttpStatus.INTERNAL_SERVER_ERROR, "팔로우 생성 처리에 실패했습니다."),
    SELF_FOLLOW_NOT_ALLOWED("F_003", HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_TARGET_NOT_FOUND("F_004", HttpStatus.BAD_REQUEST, "팔로우 대상 사용자를 찾을 수 없습니다."),

    //NOTIFICATION N
    NOTIFICATION_NOT_FOUND("N_001", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    //PLAYLIST P
    PLAYLIST_NOT_FOUND("P_001", HttpStatus.NOT_FOUND, "플레이리스트를 찾을 수 없습니다."),

    //REVIEW R
    REVIEW_NOT_FOUND("R_001", HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),

    //GLOBAL/COMMON GLB
    INVALID_REQUEST("GLB_001", HttpStatus.BAD_REQUEST, "요청 데이터가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR("GLB_999",HttpStatus.INTERNAL_SERVER_ERROR , "서버 내부 오류");


    private final String code;
    private final HttpStatus status;
    private final String message;

    ApiErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
