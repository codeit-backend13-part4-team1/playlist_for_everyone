package com.codeit.sb13.playlist_for_everyone.domain.content.dto.response;

/**
 * 콘텐츠 DTO와 S3 파일 Key를 함께 전달하는 조회 결과.
 * Presigned URL은 DB 트랜잭션 외부에서 생성한다.
 */
public record ContentResult<T>(
        T dto,
        String fileKey
) {
}
