package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCreateCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "[{index}] 요청자={0}, 상대방={1}")
    @CsvSource({
            "7fffffff-ffff-ffff-ffff-ffffffffffff, 80000000-0000-0000-0000-000000000000",
            "80000000-0000-0000-0000-000000000000, 7fffffff-ffff-ffff-ffff-ffffffffffff"
    })
    @DisplayName("요청 방향과 관계없이 UUID 문자열순으로 조회하고 조회된 Projection을 그대로 반환한다.")
    void findExistingConversation_returnsProjectionForSortedUserPair(
            String requesterIdValue,
            String otherUserIdValue
    ) {
        UUID expectedUserAId = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID expectedUserBId = UUID.fromString("80000000-0000-0000-0000-000000000000");
        ConversationCreateCommand command = new ConversationCreateCommand(
                UUID.fromString(requesterIdValue), UUID.fromString(otherUserIdValue));
        ConversationDetailProjection projection = mock(ConversationDetailProjection.class);

        given(conversationRepository.findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId()))
                .willReturn(Optional.of(projection));

        Optional<ConversationDetailProjection> result = conversationReader.findExistingConversation(command);

        assertThat(result).hasValueSatisfying(actual -> assertThat(actual).isSameAs(projection));
        verify(conversationRepository).findDetailByUserPair(expectedUserAId, expectedUserBId, command.requesterId());
    }

    @Test
    @DisplayName("대화방이 없으면 빈 Optional을 반환한다.")
    void findExistingConversation_returnsEmptyWhenConversationNotFound() {
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willReturn(Optional.empty());

        Optional<ConversationDetailProjection> result = conversationReader.findExistingConversation(command);

        assertThat(result).isEmpty();
        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
    }

    @Test
    @DisplayName("대화방 조회가 실패하면 원본 예외를 전달한다.")
    void findExistingConversation_propagatesRepositoryFailure() {
        ConversationCreateCommand command = new ConversationCreateCommand(requesterId, withUserId);
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("대화방 조회 중 DB 연결 실패");
        given(conversationRepository.findDetailByUserPair(requesterId, withUserId, requesterId))
                .willThrow(exception);

        assertThatThrownBy(() -> conversationReader.findExistingConversation(command))
                .isSameAs(exception);

        verify(conversationRepository).findDetailByUserPair(requesterId, withUserId, requesterId);
    }
}
