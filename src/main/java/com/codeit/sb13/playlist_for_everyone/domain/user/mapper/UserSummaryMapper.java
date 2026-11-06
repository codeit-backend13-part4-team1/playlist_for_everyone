package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 조회된 사용자 정보를 공통 요약 응답으로 변환한다.
 *
 *  * 사용자 및 프로필 이미지 조회는 호출 측에서 담당한다.
 *  * 호출 측은 필요한 연관 데이터를 미리 조회하거나,
 *  * 지연 로딩이 가능한 트랜잭션 안에서 변환해야 한다.
 */

@Component
@RequiredArgsConstructor
public class UserSummaryMapper {

    private final FileStorage fileStorage;

    public UserSummary toSummary(User user) {
        // RED 단계: 컴파일 가능한 골격만 만들고 구현은 다음 단계에서 진행한다.
        throw new UnsupportedOperationException("UserSummary 변환 미구현");
    }
}
