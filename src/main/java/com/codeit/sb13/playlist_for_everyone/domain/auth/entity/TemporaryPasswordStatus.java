package com.codeit.sb13.playlist_for_everyone.domain.auth.entity;

/***
 * 임시 비빌번호 만료 상태
 *
 * 만료는 상태가 아니라 expiredAt으로 판단한다. 시간이 지났다고 바꾸는 작업이 필요 없게 하기 위해서
 */
public enum TemporaryPasswordStatus {
    ACTIVE, // 사용 가능 (만료 전이면 로그인 가능)
    DISCARDED // 재발급 또는 비밀번호 변경으로 파기됨
}
