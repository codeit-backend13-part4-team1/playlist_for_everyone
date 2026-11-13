package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("대화방 조회")
class ConversationReaderTest {

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationReader conversationReader;

    private final UUID requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID withUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        conversationReader = new ConversationReader(conversationRepository);
    }

    @ParameterizedTest(name = "[{index}] 요청자={0}, 상대방={1}, 생성용={2}")
    @CsvSource({
            "7fffffff-ffff-ffff-ffff-ffffffffffff, 80000000-0000-0000-0000-000000000000, true",
            "80000000-0000-0000-0000-000000000000, 7fffffff-ffff-ffff-ffff-ffffffffffff, true",
            "7fffffff-ffff-ffff-ffff-ffffffffffff, 80000000-0000-0000-0000-000000000000, false",
            "80000000-0000-0000-0000-000000000000, 7fffffff-ffff-ffff-ffff-ffffffffffff, false"
    })
    @DisplayName("생성·조회 입력 모두 요청 방향과 관계없이 사용자 쌍을 정렬하고 요청자 정보를 유지한다.")
    void findExistingConversation_returnsProjectionForSortedUserPair(
            String requesterIdValue,
            String otherUserIdValue,
            boolean userCreateCommand
    ){
        UUID expectedUserAId = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID expectedUserBId = UUID.fromString("80000000-0000-0000-0000-000000000000");
        ConversationCreateCommand command = new ConversationCreateCommand(
                UUID.fromString(requesterIdValue), UUID.fromString(otherUserIdValue));
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);

        given(conversationRepository.findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId()))
                .willReturn(Optional.of(projection));

        Optional<ConversationDetailProjection> result =
                userCreateCommand ? conversationReader.findExistingConversation(command)
                        : conversationReader.findExistingConversation(
                                new ConversationFindCondition(command.requesterId(), command.withUserId()));

        assertThat(result).hasValueSatisfying(actual -> assertThat(actual).isSameAs(projection));
        verify(conversationRepository).findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId());
    }

    @ParameterizedTest(name = "생성용={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("생성·조회 입력 모두 대화방이 없으면 빈 Optional을 반환한다.")
    void findExistingConversation_returnsEmptyWhenConversationNotFound(boolean useCreateCommand) {
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willReturn(Optional.empty());

        Optional<ConversationDetailProjection> result =
                useCreateCommand ? conversationReader.findExistingConversation(command)
                        : conversationReader.findExistingConversation(
                                new ConversationFindCondition(command.requesterId(), command.withUserId()));

        assertThat(result).isEmpty();
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
    }

    @ParameterizedTest(name = "생성용={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("생성·조회 입력 모두 Repository 조회가 실패하면 원본 예외를 전달한다.")
    void findExistingConversation_propagatesRepositoryFailure(boolean useCreateCommand) {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("대화방 조회 중 DB 연결 실패");
        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willThrow(exception);

        assertThatThrownBy(() ->{
            if (useCreateCommand) {
                conversationReader.findExistingConversation(new ConversationCreateCommand(requesterId, withUserId));
                return;
            }
            conversationReader.findExistingConversation(new ConversationFindCondition(requesterId, withUserId));
        }).isSameAs(exception);

        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
    }
}
