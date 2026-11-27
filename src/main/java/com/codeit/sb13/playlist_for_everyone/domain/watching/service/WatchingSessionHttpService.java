package com.codeit.sb13.playlist_for_everyone.domain.watching.service;

import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentResult;
import com.codeit.sb13.playlist_for_everyone.domain.content.dto.response.ContentSummary;
import com.codeit.sb13.playlist_for_everyone.domain.content.service.ContentReader;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.domain.user.mapper.UserSummaryMapper;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.CursorResponseWatchingSessionDto;
import com.codeit.sb13.playlist_for_everyone.domain.watching.dto.request.WatchingSessionRequestByContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.ViewingSessionInfoDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchContent;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.Watcher;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.dto.watcher.WatchingSessionDto;
import com.codeit.sb13.playlist_for_everyone.global.websocket.watching.service.viewing.ViewingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionHttpService {

    private final ViewingSessionService viewingSessionService;

    private final UserRepository userRepository;
    private final UserSummaryMapper userMapper;

    private final ContentReader contentReader;

    enum Sort {
        ASC,
        DESC;

        static private Sort get(String sort){
            return switch (sort) {
                case "ASCENDING" -> ASC;
                case "DESCENDING" -> DESC;
                default -> throw new IllegalArgumentException("Invalid sort type");
            };
        }
    }

    /**
     * 현재 컨텐츠를 시청하고 있는 사용자의 초기 정보를 반환하는 매서드.
     */
    @Transactional(readOnly = true)
    public CursorResponseWatchingSessionDto<WatchingSessionDto> getCurrentSessionInfo(
            UUID contentId,
            WatchingSessionRequestByContent request
    ){
        // 객체 null 검증. -  필드 입력값은 Dto 에서 이미 검증 함.
        Objects.requireNonNull(request);

        // 1. 현재 시청중인 userId 리스트 반환.
        List<ViewingSessionInfoDto> originList = viewingSessionService.getCurrentViewerIdList(contentId);

        // 2. 리스트 정렬방향에 따라 정렬. -> ctime
        boolean ascending = Sort.get(request.sortDirection()) == Sort.ASC;

        Comparator<ViewingSessionInfoDto> order =
                Comparator.comparing(ViewingSessionInfoDto::createdAt)
                        .thenComparing(ViewingSessionInfoDto::subscriptionId);

        List<ViewingSessionInfoDto> sortedList = new ArrayList<>(originList);
        sortedList.sort(ascending ? order : order.reversed());

        //c3. limit 에 따라 트리밍
        int start = 0;

        if (request.cursor() != null) {
            Instant cursor = Instant.parse(request.cursor());
            UUID idAfter = request.idAfter();

            if (idAfter == null) {
                throw new IllegalArgumentException("커서와 함께 idAfter가 필요합니다.");
            }

            while (start < sortedList.size()) {
                ViewingSessionInfoDto dto = sortedList.get(start);
                int comparison = dto.createdAt().compareTo(cursor);

                if (comparison == 0) {
                    comparison = dto.subscriptionId().compareTo(idAfter);
                }

                if (ascending ? comparison > 0 : comparison < 0) {
                    break;
                }
                start++;
            }
        }

        int end = start + Math.min(request.limit(), sortedList.size() - start);



        List<ViewingSessionInfoDto> list = sortedList.subList(start, end);


        // 4. 반환 객체를 만들기 위한 정보 조회
        List<WatchingSessionDto> data;

        if (list.isEmpty()) {
            data = List.of();
        }
        else {
            List<UUID> targetUserList = list.stream().map(ViewingSessionInfoDto::userId).toList();
            List<UUID> targetContentList = list.stream().map(ViewingSessionInfoDto::contentId).toList();

            // 4.1. 정보 일괄 조회
            Map<UUID, UserSummary> watcherMap = userRepository.findAllWithProfileImage(targetUserList)
                    .stream().map(userMapper::toSummary).collect(Collectors.toMap(
                            UserSummary::userId,
                            s -> s
                    ));
            Map<UUID, ContentSummary> contentMap = contentReader.getSummariesByIds(targetContentList)
                    .stream().map(ContentResult::dto).collect(Collectors.toMap(
                            ContentSummary::contentId,
                            s -> s
                    ));

            // 4.2. 페이지에 들어갈 객체 조립
            data = list.stream().map(
                    dto -> {
                        UserSummary watcher = watcherMap.get(dto.userId());
                        ContentSummary content = contentMap.get(dto.contentId());
                        return new WatchingSessionDto(
                                dto.subscriptionId(),
                                dto.createdAt(),
                                new Watcher(watcher.userId(), watcher.name(), watcher.profileImageUrl()),
                                new WatchContent(
                                        content.contentId(),
                                        content.type(),
                                        content.title(),
                                        content.description(),
                                        content.thumbnailUrl(),
                                        content.tags(),
                                        content.averageRating(),
                                        content.reviewCount())
                        );
                    }
            ).toList();
        }


        // 4.3. 페이징 객체 조립
        String nextCursor = !data.isEmpty() ? data.get(data.size() - 1).createdAt().toString() : null;
        UUID nextIdAfter = !data.isEmpty() ? data.get(data.size() - 1).id() : null;
        boolean hasNext = end < sortedList.size();
        long totalCount = sortedList.size();
        String sortBy = "createdAt";
        String sortDirection = request.sortDirection();

        return new CursorResponseWatchingSessionDto<>(
                data,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                sortBy,
                sortDirection
        );
    }

    /**
     * 사용자 id 를 기반으로 현재 시청중인 세션에 대한 정보를 반환하는 매서드
     */
    @Transactional(readOnly = true)
    public void getWatchingSessionByUserId(UUID userId) {
        // 1. 유저 정보를 바탕으로, 현재 활성 세션을 검증, 반환.
    }

}
