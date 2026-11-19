package com.codeit.sb13.playlist_for_everyone.domain.review.repository;

import com.codeit.sb13.playlist_for_everyone.domain.review.dto.condition.ReviewListCondition;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.Review;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.review.entity.QReview.review;
import static com.codeit.sb13.playlist_for_everyone.domain.user.entity.QUser.user;
import static com.codeit.sb13.playlist_for_everyone.global.file.entity.QFileInfo.fileInfo;

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
                .where(
                        review.content.id.eq(contentId),
                        cursorCondition(condition)
                )
                .join(review.author, user).fetchJoin()
                .leftJoin(user.profileImage, fileInfo).fetchJoin()
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

    /**
     * 커서 이후의 리뷰를 조회하기 위한 조건을 생성한다.
     * 정렬값이 동일한 경우 UUID를 보조 정렬 기준으로 사용한다.
     */
    private BooleanExpression cursorCondition(
            ReviewListCondition condition
    ) {
        boolean hasCursor = condition.cursor() != null;
        boolean hasIdAfter = condition.idAfter() != null;

        // 첫 페이지 요청
        if (!hasCursor && !hasIdAfter) {
            return null;
        }

        // 커서와 idAfter는 함께 전달하고, 커서는 비어 있으면 안 됨
        if (hasCursor != hasIdAfter) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }

        boolean ascending =
                condition.sortDirection() == SortDirection.ASCENDING;

        UUID idAfter = condition.idAfter();

        // 정렬값이 동일한 경우 UUID로 다음 리뷰 구분
        BooleanExpression idCondition = ascending
                ? review.id.gt(idAfter)
                : review.id.lt(idAfter);

        return switch (condition.sortBy()) {
            case "createdAt" -> {
                LocalDateTime cursor =
                        LocalDateTime.parse(condition.cursor());

                // 생성일 기준 커서 비교
                yield ascending
                        ? review.createdAt.gt(cursor)
                        .or(review.createdAt.eq(cursor).and(idCondition))
                        : review.createdAt.lt(cursor)
                        .or(review.createdAt.eq(cursor).and(idCondition));
            }

            case "rating" -> {
                double cursor = Double.parseDouble(condition.cursor());

                yield ascending
                        ? review.rating.gt(cursor)
                        .or(review.rating.eq(cursor).and(idCondition))
                        : review.rating.lt(cursor)
                        .or(review.rating.eq(cursor).and(idCondition));
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준: " + condition.sortBy()
            );
        };
    }

}
