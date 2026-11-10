package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContent;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContentTag;
import com.codeit.sb13.playlist_for_everyone.domain.content.mapper.ContentMapper;
import com.codeit.sb13.playlist_for_everyone.domain.review.entity.QReview;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.codeit.sb13.playlist_for_everyone.domain.content.entity.QContent.content;

@RequiredArgsConstructor
public class ContentSearchRepositoryImpl implements ContentSearchRepository {

    private final JPAQueryFactory queryFactory;
    private final ContentMapper mapper;

    @Override
    public List<ContentSummary> search(
            ContentSearchCondition condition
    ) {
        // 리뷰 수 및 평점
        NumberExpression<Double> rating = ratingExpression();
        NumberExpression<Long> reviewCount = reviewCountExpression();

        List<Tuple> results = queryFactory
                .select(content, rating, reviewCount)
                .from(content)
                .where(
                        typeContains(condition.typeEqual()),
                        keywordContains(condition.keywordLike()),
                        tagsInContains(condition.tagsIn())
                )
                .orderBy(
                        getOrderSpecifier(
                                condition.sortBy(),
                                condition.sortDirection(),
                                rating
                        ),
                        condition.sortDirection() == SortDirection.ASCENDING
                        ? content.id.asc() : content.id.desc()
                )
                .limit(condition.limit() + 1L)
                .fetch();

        return results.stream()
                .map(tuple -> mapper.toSummary(
                        tuple.get(content),
                        tuple.get(rating),
                        tuple.get(reviewCount).intValue()
                ))
                .toList();
    }

    // 콘텐츠 타입 필터
    private BooleanExpression typeContains(ContentType type) {
        return type != null
                ? content.type.eq(type)
                : null;
    }

    // 키워드 검색
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }

        return content.title.containsIgnoreCase(keyword)
                .or(content.description.containsIgnoreCase(keyword));
    }

    // 태그 필터
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

    // 리뷰 수 서브쿼리
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

    // 평점 서브쿼리
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

}
