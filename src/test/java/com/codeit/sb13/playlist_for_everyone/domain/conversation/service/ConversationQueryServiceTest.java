package com.codeit.sb13.playlist_for_everyone.domain.conversation.service;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.response.ConversationDto;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.mapper.ConversationMapper;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationReader;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationSaver;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl.ConversationServiceImpl;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserSummary;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.ConversationNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.SelfConversationNotAllowedException;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("대화방 조회 서비스")
public class ConversationQueryServiceTest {
    @Mock
    private ConversationReader conversationReader;

    @Mock
    private ConversationSaver conversationSaver;

    @Mock
    private ConversationMapper conversationMapper;

    private ConversationService conversationService;
    private final UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(conversationReader, conversationSaver, conversationMapper);
    }

    @Test
    @DisplayName("상대방과의 기존 대화방을 반환하고 새로 저장하지 않는다.")
    void findWithUser_returnsExistingConversationWithoutSaving(){


        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);

        ConversationDto expected = new ConversationDto(UUID.randomUUID(),
                new UserSummary(withUserId, "대화 상대", null),
                null,
                false);
        ConversationFindCondition condition = new ConversationFindCondition(requesterId, withUserId);
        given(conversationReader.findExistingConversation(condition))
                .willReturn(Optional.of(projection));

        given(conversationMapper.toConversationDto(requesterId, projection))
                .willReturn(expected);

        ConversationDto result = conversationService.findWithUser(condition);
        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(conversationSaver);
    }

    @Test
    @DisplayName("상대방과의 대화방이 없으면 미존재 예외를 발생시키고 생성하지 않는다.")
    void findWithUser_throwsWhenConversationNotFoundWithoutSaving(){
        ConversationFindCondition condition = new ConversationFindCondition(requesterId, withUserId);

        given(conversationReader.findExistingConversation(condition))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findWithUser(condition))
                .isInstanceOf(ConversationNotFoundException.class);

        verifyNoInteractions(conversationSaver, conversationMapper);
    }

    @Test
    @DisplayName("자기 자신과의 대화방 조회를 요청 하면 예외가 발생하고 조회·저장하지 않는다.")
    void findWithUser_rejectsSelfConversationBeforeLookup(){
        ConversationFindCondition condition = new ConversationFindCondition(requesterId, requesterId);

        assertThatThrownBy(() -> conversationService.findWithUser(condition))
                .isInstanceOf(SelfConversationNotAllowedException.class);

        verifyNoInteractions(conversationReader, conversationSaver, conversationMapper);
    }

    @Test
    @DisplayName("대화방 단건 조회 결과를 요청자 기준 응답으로 반환하고 저장하지 않는다.")
    void findById_returnsConversationWithoutSaving(){
        UUID conversationId = UUID.randomUUID();

        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, conversationId);
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
        ConversationDto expected = new ConversationDto(conversationId,
                new UserSummary(withUserId, "대화 상대", null),
                null,
                false);

        given(conversationReader.findById(condition))
                .willReturn(Optional.of(projection));

        given(conversationMapper.toConversationDto(requesterId, projection))
                .willReturn(expected);

        ConversationDto result = conversationService.findById(condition);
        assertThat(result).isEqualTo(expected);
        verifyNoInteractions(conversationSaver);

    }

    @Test
    @DisplayName("단건 조회 결과가 없으면 미존재 예외를 발생시키고 저장·응답 변환하지 않는다.")
    void findById_throwsWhenConversationNotFound(){
        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, UUID.randomUUID());

        given(conversationReader.findById(condition))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findById(condition))
                .isInstanceOf(ConversationNotFoundException.class);

        verifyNoInteractions(conversationSaver, conversationMapper);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 요청 개수만 반환하고 마지막 반환 대화방으로 다음 커서를 만든다.")
    void search_returnsLimitedDataAndNextCursorWhenMoreResultsExist(){
        ConversationSearchCondition condition = new ConversationSearchCondition(
                null,
                null,
                null,
                2,
                SortDirection.DESCENDING,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        ConversationDetailProjection first = mock(ConversationDetailProjection.class);
        ConversationDetailProjection second = mock(ConversationDetailProjection.class);
        ConversationDetailProjection extra = mock(ConversationDetailProjection.class);

        UUID secondId = UUID.randomUUID();
        LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 11, 12, 10, 0, 0, 123456000);

        Conversation secondConversation = mock(Conversation.class);
        given(second.getConversation()).willReturn(secondConversation);
        given(secondConversation.getId()).willReturn(secondId);
        given(secondConversation.getCreatedAt()).willReturn(secondCreatedAt);
        ConversationDto firstDto = new ConversationDto(
                UUID.randomUUID(),
                new UserSummary(withUserId, "첫 번째 상대", null),
                null,
                false
        );
        ConversationDto secondDto = new ConversationDto(
                secondId,
                new UserSummary(UUID.randomUUID(), "두 번째 상대", null),
                null,
                false
        );
        given(conversationReader.search(condition))
                .willReturn(List.of(first, second, extra));
        given(conversationReader.searchCount(condition))
                .willReturn(7L);
        given(conversationMapper.toConversationDto(requesterId, first))
                .willReturn(firstDto);
        given(conversationMapper.toConversationDto(requesterId,second))
                .willReturn(secondDto);

        CursorResponse<ConversationDto> result = conversationService.search(condition);
        assertThat(result.data()).containsExactly(firstDto, secondDto);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(secondCreatedAt.toString());
        assertThat(result.nextIdAfter()).isEqualTo(secondId);
        assertThat(result.totalCount()).isEqualTo(7L);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);
        verifyNoInteractions(conversationSaver);
    }

    @ParameterizedTest(name = "조회 결과가 {0}개이면 다음 페이지가 없다.")
    @ValueSource(ints = {0,1,2})
    @DisplayName("조회 결과가 요청 개수 이하이면 다음 커서 없이 마지막 페이즈를 반환하다.")
    void search_returnsLastPageWithoutNextCursor(int resultCount){
        ConversationSearchCondition condition = new ConversationSearchCondition(
                null,
                "2026-11-12T09:00:00",
                UUID.randomUUID(),
                2,
                SortDirection.ASCENDING,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        List<ConversationDetailProjection> projections = new ArrayList<>();
        List<ConversationDto> expected = new ArrayList<>();

        IntStream.range(0, resultCount).forEach(i -> {
            ConversationDetailProjection projection = mock(ConversationDetailProjection.class);
            ConversationDto dto = new ConversationDto(
                    UUID.randomUUID(),
                    new UserSummary(UUID.randomUUID(), "대화 상대 " + i, null),
                    null,
                    false
            );
            projections.add(projection);
            expected.add(dto);
            given(conversationMapper.toConversationDto(requesterId, projection))
                    .willReturn(dto);
        });
        given(conversationReader.search(condition)).willReturn(projections);
        given(conversationReader.searchCount(condition)).willReturn(7L);

        CursorResponse<ConversationDto> result = conversationService.search(condition);
        assertThat(result.data()).containsExactlyElementsOf(expected);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.nextIdAfter()).isNull();
        assertThat(result.totalCount()).isEqualTo(7L);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING);
        verifyNoInteractions(conversationSaver);
    }

}
