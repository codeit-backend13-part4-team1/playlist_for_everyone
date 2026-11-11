package com.codeit.sb13.playlist_for_everyone.domain.user.repository.impl;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserCustomRepository;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.user.entity.QUser.user;
import static com.codeit.sb13.playlist_for_everyone.global.file.entity.QFileInfo.fileInfo;


@RequiredArgsConstructor
public class UserCustomRepositoryImpl implements UserCustomRepository {

    private final JPAQueryFactory queryFactory;


    @Override
    public List<User> search(UserSearchCondition condition) {
        return queryFactory
                .selectFrom(user)
                // 응답에 프로필 이미지 URL이 필요하므로 함께 가져온다. 이미지가 없는 사용자도 포함한다.
                .leftJoin(user.profileImage, fileInfo).fetchJoin()
                .where(filters(condition))
                .where(cursorCondition(condition))
                .orderBy(orderSpecifiers(condition))
                .limit(condition.limit() + 1L)
                .fetch();
    }

    @Override
    public long count(UserSearchCondition condition) {
        Long count = queryFactory
                .select(user.count())
                .from(user)
                .where(filters(condition))
                .fetchOne();
        return count == null ? 0L : count;
    }

    // ---------- 필터 (search와 count가 함께 사용)
    private BooleanExpression[] filters(UserSearchCondition condition) {
        return new BooleanExpression[]{
                emailContains(condition.emailLike()),
                roleEquals(condition.roleEqual()),
                lockedEquals(condition.isLocked())
        };
    }

    private BooleanExpression emailContains(String emailLike) {
        return StringUtils.hasText(emailLike) ? user.email.containsIgnoreCase(emailLike) : null;
    }

    private BooleanExpression roleEquals(Role role) {
        return role == null ? null : user.role.eq(role);
    }

    // DB에는 enabled(정상 사용 가능)로 저장된다. isLocked = !enabled
    private BooleanExpression lockedEquals(Boolean isLocked) {
        return isLocked == null ? null : user.enabled.eq(!isLocked);
    }

    // ---------- 정렬 키

    // 이름이 없는 사용자도 비교할 수 있도록 빈 문자열로 바꾼다.
    private StringExpression nameKey() {
        return user.name.coalesce("");
    }

    // API의 isLocked 값을 그대로 만든다. 'false' < 'true'이므로 오름차순이면 잠기지 않은 사용자가 먼저 온다.
    private StringExpression lockedKey() {
        return new CaseBuilder()
                .when(user.enabled.isTrue()).then("false")
                .otherwise("true");
    }

    // 권한은 이름(ADMIN, USER)으로 저장되므로 문자열로 비교한다.
    private StringExpression roleKey() {
        return user.role.stringValue();
    }

    private ComparableExpressionBase<?> sortKey(UserSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> nameKey();
            case EMAIL -> user.email;
            case CREATED_AT -> user.createdAt;
            case IS_LOCKED -> lockedKey();
            case ROLE -> roleKey();
        };
    }

    // ---------- 커서 (search에서만 사용)

    private BooleanExpression cursorCondition(UserSearchCondition condition) {
        if (!condition.hasCursor()) {
            return null;
        }
        String cursor = condition.cursor();
        return switch (condition.sortBy()) {
            case NAME -> after(nameKey(), cursor, condition);
            case EMAIL -> after(user.email, cursor, condition);
            case CREATED_AT -> after(user.createdAt, LocalDateTime.parse(cursor), condition);
            case IS_LOCKED -> after(lockedKey(), cursor, condition);
            case ROLE -> after(roleKey(), cursor, condition);
        };
    }

    /**
     * 커서 다음 위치의 조건.
     * 정렬 키가 커서보다 뒤이거나, 키가 같으면 id가 idAfter보다 뒤인 행이다.
     * 정렬 값이 같은 사용자가 여러 명이어도 id로 순서가 정해져 중복·누락이 생기지 않는다.
     */
    private <T extends Comparable<?>> BooleanExpression after(
            ComparableExpression<T> key, T cursor, UserSearchCondition condition) {
        UUID idAfter = condition.idAfter();
        if (condition.isDesc()) {
            return key.lt(cursor)
                    .or(key.eq(cursor).and(user.id.lt(idAfter)));
        }
        return key.gt(cursor)
                .or(key.eq(cursor).and(user.id.gt(idAfter)));
    }

    // ---------- 정렬

    // 정렬 키가 같을 때 순서를 고정하기 위해 id를 같은 방향으로 함께 정렬한다.
    private OrderSpecifier<?>[] orderSpecifiers(UserSearchCondition condition) {
        boolean desc = condition.isDesc();
        return new OrderSpecifier<?>[] {
                order(sortKey(condition.sortBy()), desc),
                order(user.id, desc)
        };
    }

    private OrderSpecifier<?> order(ComparableExpressionBase<?> expression, boolean desc) {
        return desc ? expression.desc() : expression.asc();
    }

}
