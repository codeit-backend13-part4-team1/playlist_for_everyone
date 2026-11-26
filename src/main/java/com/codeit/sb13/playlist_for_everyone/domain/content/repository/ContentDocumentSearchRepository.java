package com.codeit.sb13.playlist_for_everyone.domain.content.repository;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.condition.ContentSearchCondition;

import java.util.List;
import java.util.UUID;

public interface ContentDocumentSearchRepository {

    List<UUID> searchContentIds(ContentSearchCondition condition);

}
