package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;

import java.util.List;

public interface UserCustomRepository {


    /**
     * 필터 커서 정렬을 적용해 limit + 1개를 조회한다.
     * 하나를 더 가져와 다음 페이지 존재 여부 판단
     */
    List<User> search(UserSearchCondition condition);

    /**
     * 필터만 적용한 전체 개수. 커서는 반영하지 않는다.
     */
    long count(UserSearchCondition condition);
}
