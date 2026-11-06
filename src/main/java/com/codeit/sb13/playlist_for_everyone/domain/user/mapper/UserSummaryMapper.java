package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 조회된 사용자 정보를 공통 요약 응답으로 변환한다.
 *
 * 사용자 및 프로필 이미지 조회는 호출 측에서 담당한다.
 * 호출 측은 필요한 연관 데이터를 미리 조회하거나,
 * 지연 로딩이 가능한 트랜잭션 안에서 변환해야 한다.
 */

@Component
@RequiredArgsConstructor
public class UserSummaryMapper {

    private final FileStorage fileStorage;

    public UserSummary toSummary(User user) {
        Objects.requireNonNull(user, "요약 정보로 변환할 사용자는 필수입니다.");

        FileInfo profileImage = user.getProfileImage();

        // 이미지가 없으면 저장소를 호출하지 않는다.
        // 이미지가 있으면 이미 조회된 파일의 Key로 URL을 생성한다.
        String profileImageUrl = profileImage == null
                ? null
                : fileStorage.getFileUrl(profileImage.getKey());

        return new UserSummary(
                user.getId(),
                user.getName(),
                profileImageUrl
        );
    }
}
