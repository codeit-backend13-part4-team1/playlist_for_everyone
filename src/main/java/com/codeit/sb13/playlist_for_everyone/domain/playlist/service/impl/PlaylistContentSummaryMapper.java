package com.codeit.sb13.playlist_for_everyone.domain.playlist.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.playlist.dto.response.PlaylistContentSummary;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠 데이터를 응답 요약으로 변환한다. 트랜잭션 밖에서 호출한다.
 * 임시 구현: 파일이 있으면 Key로 URL을 만들고 없으면 기존 thumbnailUrl을 쓰는 규칙은
 * ContentService.generateThumbnailUrl과 같다. 콘텐츠 서비스로 이관하면 함께 삭제한다.
 */
@Component
@RequiredArgsConstructor
public class PlaylistContentSummaryMapper {

  private final FileStorage fileStorage;

  public PlaylistContentSummary toSummary(PlaylistContentData data) {
    String thumbnailUrl = data.fileKey() == null
        ? data.thumbnailUrl()
        : fileStorage.getFileUrl(data.fileKey());

    return new PlaylistContentSummary(
        data.id(), data.type(), data.title(), data.description(),
        thumbnailUrl, data.tags(), data.averageRating(), data.reviewCount());
  }
}