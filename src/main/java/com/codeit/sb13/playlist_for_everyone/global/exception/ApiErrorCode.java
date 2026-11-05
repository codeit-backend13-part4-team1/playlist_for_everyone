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

    // FILE FL
    FILE_NOT_FOUND("FL_001", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_UPLOAD_FAILED("FL_002", HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    FILE_DELETE_FAILED("FL_003", HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제에 실패했습니다."),
    FILE_INVALID_TYPE("FL_004", HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_EMPTY("FL_005", HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다."),


    //FOLLOW F
    FOLLOW_NOT_FOUND("F_001", HttpStatus.NOT_FOUND, "팔로우를 찾을 수 없습니다."),
    FOLLOW_CREATION_FAILED("F_002", HttpStatus.INTERNAL_SERVER_ERROR, "팔로우 생성 처리에 실패했습니다."),
    SELF_FOLLOW_NOT_ALLOWED("F_003", HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_TARGET_NOT_FOUND("F_004", HttpStatus.BAD_REQUEST, "팔로우 대상 사용자를 찾을 수 없습니다."),

    //NOTIFICATION N
    NOTIFICATION_NOT_FOUND("N_001", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    //PLAYLIST P
    PLAYLIST_NOT_FOUND("P_001", HttpStatus.NOT_FOUND, "플레이리스트를 찾을 수 없습니다."),
    // P_002는 사용하지 않음 (소유자 검사를 PlaylistGuard로 통일하며 제거)
    PLAYLIST_CONTENT_DUPLICATED("P_003", HttpStatus.BAD_REQUEST, "이미 플레이리스트에 담긴 콘텐츠입니다."),
    PLAYLIST_CONTENT_NOT_FOUND("P_004", HttpStatus.BAD_REQUEST, "플레이리스트에 담기지 않은 콘텐츠입니다."),
    PLAYLIST_SUBSCRIBE_OWN("P_005", HttpStatus.BAD_REQUEST, "본인의 플레이리스트는 구독할 수 없습니다."),
    PLAYLIST_SUBSCRIPTION_DUPLICATED("P_006", HttpStatus.BAD_REQUEST, "이미 구독한 플레이리스트입니다."),
    PLAYLIST_SUBSCRIPTION_NOT_FOUND("P_007", HttpStatus.BAD_REQUEST, "구독하지 않은 플레이리스트입니다."),

    //REVIEW R
    REVIEW_NOT_FOUND("R_001", HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),

    //SSE S
    SSE_PUBLISH_FAIL("S_001", HttpStatus.INTERNAL_SERVER_ERROR, "SSE 이벤트 발행에 실패했습니다."),

    // WEBSOCKET W
    WEBSOCKET_DESTINATION_INVALID("W_001", HttpStatus.NOT_FOUND, "유효하지 않은 웹소켓 경로입니다."),
    WEBSOCKET_AUTHORIZATION_INVALID("W_002", HttpStatus.UNAUTHORIZED, "권한이 존재하지 않습니다."),
    WEBSOCKET_SESSION_NOT_FOUND("W_003", HttpStatus.NOT_FOUND, "웹소켓 세션이 존재하지 않습니다."),

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
