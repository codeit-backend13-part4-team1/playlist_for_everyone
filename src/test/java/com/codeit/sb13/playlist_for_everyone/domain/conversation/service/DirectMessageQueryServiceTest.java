package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.DirectMessageSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.MessageDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessageSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.DirectMessageMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageReader;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.DirectMessageUpdater;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DM 메시지 조회 서비스")
public class DirectMessageQueryServiceTest {

    @Mock
    private DirectMessageReader directMessageReader;

    @Mock
    private DirectMessageMapper directMessageMapper;

    @Mock
    private DirectMessageUpdater directMessageUpdater;

    private DirectMessageService directMessageService;

    private final UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID counterpartId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID conversationId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp(){
        directMessageService = new DirectMessageServiceImpl(
                directMessageReader,
                directMessageMapper,
                directMessageUpdater);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 요청 개수만 반환하고 마지막 반환 메시지로 다음 커서를 만든다.")
    void search_returnsLimitedDataAndNextCursorWhenMoreResultsExist(){
        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(
                conversationId,
                null,
                null,
                2,
                SortDirection.DESCENDING,
                DirectMessageSortBy.CREATED_AT,
                requesterId
        );
        DirectMessage first = mock(DirectMessage.class);
        DirectMessage second = mock(DirectMessage.class);
        DirectMessage extra = mock(DirectMessage.class);

        UUID secondId = UUID.randomUUID();
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 11, 14, 10, 0, 0, 123_456_000);

        given(second.getId()).willReturn(secondId);
        given(second.getCreatedAt()).willReturn(secondCreatedAt);

        UserSummary requesterSummary = new UserSummary(requesterId, "요청자", null);
        UserSummary counterPartSummary = new UserSummary(counterpartId, "상대방", null);
        MessageDto firstDto = new MessageDto(UUID.randomUUID(),
                conversationId,
                secondCreatedAt.plusSeconds(1),
                requesterSummary,
                counterPartSummary,
                "첫 번째 메시지");
        MessageDto secondDto = new MessageDto(secondId,
                conversationId,
                secondCreatedAt,
                counterPartSummary,
                requesterSummary,
                "두 번째 메시지");

        given(directMessageReader.search(condition))
                .willReturn(List.of(first, second, extra));
        given(directMessageReader.searchCount(condition))
                .willReturn(7L);
        given(directMessageMapper.toMessageDto(first))
                .willReturn(firstDto);
        given(directMessageMapper.toMessageDto(second))
                .willReturn(secondDto);

        CursorResponse<MessageDto> result = directMessageService.search(condition);
        assertThat(result.data()).containsExactly(firstDto, secondDto);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(secondCreatedAt.toString());
        assertThat(result.nextIdAfter()).isEqualTo(secondId);
        assertThat(result.totalCount()).isEqualTo(7L);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
        verify(directMessageMapper, never()).toMessageDto(extra);

    }

    @ParameterizedTest(name = "조회 결과가 {0}개이면 다음 페이지가 없다")
    @ValueSource(ints = {0,1,2})
    @DisplayName("조회 결과가 요청 개수 이하이면 다음 커서 없이 마지막 페이지를 반환한다.")
    void search_returnsLastPageWithoutNextCursor( int resultCount){
        DirectMessageSearchCondition condition = new DirectMessageSearchCondition(
                conversationId,
                "2026-11-14T09:00:00",
                UUID.randomUUID(),
                2,
                SortDirection.ASCENDING,
                DirectMessageSortBy.CREATED_AT,
                requesterId
        );

        List<DirectMessage> messages = new ArrayList<>();
        List<MessageDto> expected = new ArrayList<>();

        UserSummary requesterSummary = new UserSummary(requesterId, "요청자", null);
        UserSummary counterPartSummary = new UserSummary(counterpartId, "상대방", null);

        LocalDateTime base = LocalDateTime.of(2026, 11, 14, 10, 0);

        IntStream.range(0, resultCount).forEach(index -> {
            DirectMessage message = mock(DirectMessage.class);
            MessageDto dto = new MessageDto(
                    UUID.randomUUID(),
                    conversationId,
                    base.plusSeconds(index),
                    requesterSummary,
                    counterPartSummary,
                    "메시지 " + index
            );
            messages.add(message);
            expected.add(dto);

            given(directMessageMapper.toMessageDto(message))
                    .willReturn(dto);
        });
        given(directMessageReader.search(condition)).willReturn(messages);
        given(directMessageReader.searchCount(condition)).willReturn(7L);

        CursorResponse<MessageDto> result = directMessageService.search(condition);
        assertThat(result.data()).containsExactlyElementsOf(expected);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
        assertThat(result.totalCount()).isEqualTo(7L);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING);
    }




}
