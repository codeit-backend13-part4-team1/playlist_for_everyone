package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentSource;
import com.codeit.sb13.playlist_for_everyone.domain.content.entity.ContentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentSearchRepository {

    /** 콘텐츠 상세 조회 */
    @Query("""
        SELECT
            c AS content,
            COALESCE(rs.averageRating, 0.0) AS averageRating,
            COALESCE(rs.reviewCount, 0L) AS reviewCount
        FROM Content c
        LEFT JOIN (
            SELECT
                r.content.id AS contentId,
                AVG(r.rating) AS averageRating,
                COUNT(r.id) AS reviewCount
            FROM Review r
            WHERE r.content.id = :contentId
            GROUP BY r.content.id
        ) rs ON rs.contentId = c.id
        WHERE c.id = :contentId
        """)
    Optional<ContentDetailProjection> findDetailById(
            @Param("contentId") UUID contentId
    );

    /** Elasticsearch 인덱싱을 위한 콘텐츠 ID 페이징 조회 */
    @Query("SELECT c.id FROM Content c ORDER BY c.id ASC")
    Slice<UUID> findAllIds(Pageable pageable);

    /**
     * 초기 색인을 위해 마지막으로 조회한 콘텐츠 ID 이후의 ID 목록을 조회한다.
     * Offset 페이징으로 인한 콘텐츠 삭제 시 조회 누락을 방지한다.
     */
    @Query("""
        SELECT c.id
        FROM Content c
        WHERE c.id > :lastId
        ORDER BY c.id ASC
        """)
    Slice<UUID> findAllIdsAfter(
            @Param("lastId") UUID lastId,
            Pageable pageable
    );

    /** 콘텐츠 및 연관 태그·파일 정보 일괄 조회 */
    @EntityGraph(attributePaths = {"fileInfo", "tags"})
    @Query("SELECT DISTINCT c FROM Content c WHERE c.id IN :contentIds")
    List<Content> findAllByIds(
            @Param("contentIds") Collection<UUID> contentIds
    );


    Optional<Content> findByProviderAndTypeAndProviderItemId(
            ContentSource provider,
            ContentType type,
            String providerItemId
    );

    /** 같은 외부 콘텐츠가 없을 때만 저장 */
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO contents (
            id, type, title, description, thumbnail_url,
            provider, provider_item_id, created_at, updated_at
        )
        VALUES (
            :id, :type, :title, :description, :thumbnailUrl,
            :provider, :providerItemId, :now, :now
        )
        ON CONFLICT ON CONSTRAINT uk_content_provider_type_item_id
        DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("type") String type,
            @Param("title") String title,
            @Param("description") String description,
            @Param("thumbnailUrl") String thumbnailUrl,
            @Param("provider") String provider,
            @Param("providerItemId") String providerItemId,
            @Param("now") LocalDateTime now
    );
}
