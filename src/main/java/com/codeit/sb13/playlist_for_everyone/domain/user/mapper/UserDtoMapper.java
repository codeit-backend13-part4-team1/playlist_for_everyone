package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 조회된 사용자를 응답으로 변환한다.
 *
 * 프로필 이미지는 호출 측에서 함께 조회해 두어야 한다.
 * 파일 ID로 다시 조회하지 않고, 이미 가져온 파일의 key로 URL을 만든다.
 */
@Component
@RequiredArgsConstructor
public class UserDtoMapper {

    private final FileStorage fileStorage;

    public UserDto toDto(User user) {

        Objects.requireNonNull(user, "응답으로 변환할 사용자는 필수입니다.");

        FileInfo profileImage = user.getProfileImage();

        // 이미지가 없으면 저장소를 호출하지 않는다.
        // 이미 조회된 파일의 Key로 URL을 만들어 사용자마다 파일을 다시 조회하지 않는다.
        String profileImageUrl = profileImage == null
                ? null
                : fileStorage.getFileUrl(profileImage.getKey());

        return new UserDto(
                user.getId(),
                user.getCreatedAt(),
                user.getEmail(),
                user.getName(),
                profileImageUrl,
                user.getRole(),
                user.isLocked()
        );
    }
}
