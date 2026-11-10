package com.codeit.sb13.playlist_for_everyone.domain.user.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserCustomRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class UserCustomRepositoryImpl implements UserCustomRepository {

    private final JPAQueryFactory queryFactory;


    @Override
    public List<User> search(UserSearchCondition condition) {
        // RED: 다음 단계에서 구현
        return List.of();
    }

    @Override
    public long count(UserSearchCondition condition) {
        // RED: 다음 단계에서 구현
        return 0;
    }
}
