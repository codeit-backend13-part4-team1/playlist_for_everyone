package com.codeit.sb13.playlist_for_everyone.domain.user.mapper;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        // RED: 다음 단계에서 구현
        return new UserDto(null, null, null,
                null, null, null, false);
    }
}
