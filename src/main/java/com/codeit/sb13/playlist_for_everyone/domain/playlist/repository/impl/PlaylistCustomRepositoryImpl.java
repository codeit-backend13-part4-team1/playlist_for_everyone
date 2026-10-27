package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.impl;

import static com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.QPlaylist.playlist;
import static com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.QSubscription.subscription;
import static com.codeit.sb13.playlist_for_everyone.domain.user.entity.QUser.user;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.Playlist;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistCustomRepository;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.repository.PlaylistSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class PlaylistCustomRepositoryImpl implements PlaylistCustomRepository {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Playlist> search(PlaylistSearchCondition condition) {
    return queryFactory
        .selectFrom(playlist)
        .join(playlist.owner, user).fetchJoin()
        .where(filters(condition))
        .where(cursorCondition(condition))
        .orderBy(orderSpecifiers(condition))
        .limit(condition.limit() + 1L)
        .fetch();
  }

  @Override
  public long count(PlaylistSearchCondition condition) {
    Long count = queryFactory
        .select(playlist.count())
        .from(playlist)
        .where(filters(condition))
        .fetchOne();
    return count == null ? 0L : count;
  }

  // ---------- 필터 (search와 count가 함께 사용)

  private BooleanExpression[] filters(PlaylistSearchCondition condition) {
    return new BooleanExpression[] {
        keywordContains(condition.keywordLike()),
        ownerIdEquals(condition.ownerIdEqual()),
        subscribedBy(condition.subscriberIdEqual())
    };
  }

  private BooleanExpression keywordContains(String keyword) {
    if (!StringUtils.hasText(keyword)) {
      return null;
    }
    return playlist.title.containsIgnoreCase(keyword)
        .or(playlist.description.containsIgnoreCase(keyword));
  }

  private BooleanExpression ownerIdEquals(UUID ownerId) {
    return ownerId == null ? null : playlist.owner.id.eq(ownerId);
  }

  private BooleanExpression subscribedBy(UUID subscriberId) {
    if (subscriberId == null) {
      return null;
    }
    return JPAExpressions.selectOne()
        .from(subscription)
        .where(
            subscription.playlistId.eq(playlist.id),
            subscription.subscriberId.eq(subscriberId))
        .exists();
  }

  // ---------- 커서 (search에서만 사용)

  private BooleanExpression cursorCondition(PlaylistSearchCondition condition) {
    if (!condition.hasCursor()) {
      return null;
    }
    return switch (condition.sortBy()) {
      case UPDATED_AT -> updatedAtAfterCursor(condition);
      case SUBSCRIBE_COUNT -> subscribeCountAfterCursor(condition);
    };
  }

  private BooleanExpression updatedAtAfterCursor(PlaylistSearchCondition condition) {
    LocalDateTime cursor = LocalDateTime.parse(condition.cursor());
    UUID idAfter = condition.idAfter();
    if (condition.isDesc()) {
      return playlist.updatedAt.lt(cursor)
          .or(playlist.updatedAt.eq(cursor).and(playlist.id.lt(idAfter)));
    }
    return playlist.updatedAt.gt(cursor)
        .or(playlist.updatedAt.eq(cursor).and(playlist.id.gt(idAfter)));
  }

  private BooleanExpression subscribeCountAfterCursor(PlaylistSearchCondition condition) {
    long cursor = Long.parseLong(condition.cursor());
    UUID idAfter = condition.idAfter();
    if (condition.isDesc()) {
      return playlist.subscriberCount.lt(cursor)
          .or(playlist.subscriberCount.eq(cursor).and(playlist.id.lt(idAfter)));
    }
    return playlist.subscriberCount.gt(cursor)
        .or(playlist.subscriberCount.eq(cursor).and(playlist.id.gt(idAfter)));
  }

  // ---------- 정렬

  private OrderSpecifier<?>[] orderSpecifiers(PlaylistSearchCondition condition) {
    ComparableExpressionBase<?> sortKey = switch (condition.sortBy()) {
      case UPDATED_AT -> playlist.updatedAt;
      case SUBSCRIBE_COUNT -> playlist.subscriberCount;
    };
    return new OrderSpecifier<?>[] {
        order(sortKey, condition.isDesc()),
        order(playlist.id, condition.isDesc())
    };
  }

  private OrderSpecifier<?> order(ComparableExpressionBase<?> expression, boolean desc) {
    return desc ? expression.desc() : expression.asc();
  }
}