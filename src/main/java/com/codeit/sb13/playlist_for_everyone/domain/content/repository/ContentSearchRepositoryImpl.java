package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSearchResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.QReview;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContent.content;

@RequiredArgsConstructor
public class ContentSearchRepositoryImpl implements ContentSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public long countBySearchCondition(ContentSearchCondition condition) {
        Long count = queryFactory
                .select(content.count())
                .from(content)
                .where(
                        typeContains(condition.typeEqual()),
                        keywordContains(condition.keywordLike()),
                        tagsInContains(condition.tagsIn())
                )
                .fetchOne();

        return count != null ? count : 0L;
    }

    /**
     * 검색 조건에 따라 콘텐츠를 조회한다.
     * 리뷰 평균 평점과 리뷰 수를 함께 조회하며,
     * 다음 페이지 존재 여부를 확인하기 위해 limit + 1개를 조회한다.
     */
    @Override
    public List<ContentSearchResult> searchByDatabase(
            ContentSearchCondition condition
    ) {
        // 리뷰 수 및 평점
        NumberExpression<Double> rating = ratingExpression();
        NumberExpression<Long> reviewCount = reviewCountExpression();

        List<ContentSearchResult> results = queryFactory
                .select(Projections.constructor(
                                ContentSearchResult.class,
                                content,
                                rating,
                                reviewCount
                        ))
                .from(content)
                .where(
                        typeContains(condition.typeEqual()),
                        keywordContains(condition.keywordLike()),
                        tagsInContains(condition.tagsIn()),
                        cursorCondition(condition, rating)
                )
                .orderBy(
                        getOrderSpecifier(
                                condition.sortBy(),
                                condition.sortDirection(),
                                rating
                        ),
                        // 정렬값이 동일한 경우 UUID로 순서 보장
                        condition.sortDirection() == SortDirection.ASCENDING
                                ? content.id.asc() : content.id.desc()
                )
                .limit(condition.limit() + 1L)
                .fetch();

        return results;
    }

    // 시청자 수를 서비스에서 별도로 Redis 값을 가져오기 위한 메서드
    @Override
    public List<ContentSearchResult> searchForWatcherCount(
            ContentSearchCondition condition
    ) {
        NumberExpression<Double> rating = ratingExpression();
        NumberExpression<Long> reviewCount = reviewCountExpression();

        return queryFactory
                .select(Projections.constructor(
                        ContentSearchResult.class,
                        content,
                        rating,
                        reviewCount
                ))
                .from(content)
                .where(
                        typeContains(condition.typeEqual()),
                        keywordContains(condition.keywordLike()),
                        tagsInContains(condition.tagsIn())
                )
                .fetch();
    }

    // 콘텐츠 타입 필터 (null이면 조건 제외)
    private BooleanExpression typeContains(ContentType type) {
        return type != null
                ? content.type.eq(type)
                : null;
    }

    // 제목 또는 설명에 키워드가 포함된 콘텐츠 검색 (대소문자 무시)
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return content.title.containsIgnoreCase(keyword)
                .or(content.description.containsIgnoreCase(keyword));
    }

    // 요청한 태그 중 하나 이상을 가진 콘텐츠 검색
    // EXISTS 서브쿼리로 콘텐츠 중복 조회 방지
    private BooleanExpression tagsInContains(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }

        QContentTag contentTag = QContentTag.contentTag;

        return JPAExpressions
                .selectOne()
                .from(contentTag)
                .where(
                        contentTag.content.eq(content),
                        contentTag.tag.in(tags)
                )
                .exists();
    }

    // 콘텐츠별 리뷰 수 조회 (리뷰가 없으면 0)
    private NumberExpression<Long> reviewCountExpression() {
        QReview review = new QReview("reviewCount");

        return Expressions.numberTemplate(
                Long.class,
                "coalesce(({0}), 0)",
                JPAExpressions
                        .select(review.count())
                        .from(review)
                        .where(review.content.id.eq(content.id))
        );
    }

    // 콘텐츠별 평균 평점 조회 (리뷰가 없으면 0.0)
    private NumberExpression<Double> ratingExpression() {
        QReview review = new QReview("rating");

        return Expressions.numberTemplate(
                Double.class,
                "coalesce(({0}), 0.0)",
                JPAExpressions
                        .select(review.rating.avg())
                    .from(review)
                    .where(review.content.id.eq(content.id))
        );
    }

    // 정렬 기준과 방향에 따라 생성일 또는 평균 평점 정렬 조건 생성
    private OrderSpecifier<?> getOrderSpecifier(
            String sortBy,
            SortDirection direction,
            NumberExpression<Double> rating
    ) {
        Order order = direction == SortDirection.ASCENDING
                ? Order.ASC
                : Order.DESC;

        return switch (sortBy) {
            case "createdAt" ->
                    new OrderSpecifier<>(order, content.createdAt);
            case "rate" ->
                    new OrderSpecifier<>(order, rating);

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준: " + sortBy
            );
        };
    }

    /**
     * 커서 이후의 콘텐츠를 조회하기 위한 조건을 생성한다.
     * 정렬값이 동일한 경우 UUID를 보조 정렬 기준으로 사용한다.
     */
    private BooleanExpression cursorCondition(
            ContentSearchCondition condition,
            NumberExpression<Double> rating
    ) {
        boolean hasCursor = StringUtils.hasText(condition.cursor());
        boolean hasIdAfter = condition.idAfter() != null;

        // 첫 페이지 요청은 커서 조건 없이 조회
        if (!hasCursor && !hasIdAfter) {
            return null;
        }

        // cursor와 idAfter는 반드시 함께 전달해야 한다.
        if (hasCursor != hasIdAfter) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달해야 합니다."
            );
        }

        boolean ascending =
                condition.sortDirection() == SortDirection.ASCENDING;

        UUID idAfter = condition.idAfter();

        // 정렬값이 동일한 경우 UUID로 다음 콘텐츠 구분
        BooleanExpression idCondition = ascending
                ? content.id.gt(idAfter)
                : content.id.lt(idAfter);

        return switch (condition.sortBy()) {
            case "createdAt" -> {
                LocalDateTime cursor =
                        LocalDateTime.parse(condition.cursor());

                // 생성일 기준 커서 비교
                yield ascending
                        ? content.createdAt.gt(cursor)
                        .or(content.createdAt.eq(cursor).and(idCondition))
                        : content.createdAt.lt(cursor)
                        .or(content.createdAt.eq(cursor).and(idCondition));
            }

            case "rate" -> {
                double cursor = Double.parseDouble(condition.cursor());

                // 평균 평점 기준 커서 비교
                yield ascending
                        ? rating.gt(cursor)
                        .or(rating.eq(cursor).and(idCondition))
                        : rating.lt(cursor)
                        .or(rating.eq(cursor).and(idCondition));
            }

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 정렬 기준: " + condition.sortBy()
            );
        };
    }

}
