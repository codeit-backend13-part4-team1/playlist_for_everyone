package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContent.content;
import static com.codeit.sb13.playlist_for_everyone.domain.review.entity.QReview.review;

@RequiredArgsConstructor
public class ReviewListRepositoryImpl implements ReviewListRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Review> getReviewsByContentId(UUID contentId, ReviewListCondition condition) {
        Order order = condition.sortDirection() == SortDirection.ASCENDING
                ? Order.ASC
                : Order.DESC;

        return queryFactory
                .selectFrom(review)
                .where(review.content.id.eq(contentId))
                .orderBy(
                        getOrderSpecifier(
                                condition.sortBy(),
                                condition.sortDirection()
                        ),
                        new OrderSpecifier<>(order, review.id)
                )
                .limit(condition.limit() + 1L)
                .fetch();
    }

    private OrderSpecifier<?> getOrderSpecifier(
            String sortBy,
            SortDirection direction
    ) {
        Order order = direction == SortDirection.ASCENDING
                ? Order.ASC
                : Order.DESC;

        return switch (sortBy) {
            case "createdAt" ->
                    new OrderSpecifier<>(order, review.createdAt);
            case "rating" ->
                    new OrderSpecifier<>(order, review.rating);

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준: " + sortBy
            );
        };
    }

}
