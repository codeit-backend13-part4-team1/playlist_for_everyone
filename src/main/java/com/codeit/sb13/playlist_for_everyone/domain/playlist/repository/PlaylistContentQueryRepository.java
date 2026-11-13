package com.codeit.sb13.playlist_for_everyone.domain.playlist.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.entity.Content;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 플레이리스트 응답의 contents 조립용 조회 전용 repository.
 *
 * 임시 구현: 콘텐츠 도메인에 id 묶음 조회가 없어 플레이리스트 패키지에 둔다.
 * 콘텐츠 서비스에 묶음 요약 조회가 생기면 이관 후 삭제한다. (#NNN)
 */
public interface PlaylistContentQueryRepository extends Repository<Content, UUID> {

  @EntityGraph(attributePaths = {"fileInfo"})
  @Query("select c from Content c where c.id in :contentIds")
  List<Content> findAllByIds(@Param("contentIds") Collection<UUID> contentIds);

  @Query("""
      select r.content.id as contentId,
             avg(r.rating) as averageRating,
             count(r.id) as reviewCount
      from Review r
      where r.content.id in :contentIds
      group by r.content.id
      """)
  List<ContentRatingProjection> findRatingsByContentIds(
      @Param("contentIds") Collection<UUID> contentIds);
}