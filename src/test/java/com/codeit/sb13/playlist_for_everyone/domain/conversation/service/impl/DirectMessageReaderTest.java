package com.codeit.sb13.playlist_for_everyone.domain.conversation.service.impl;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.repository.DirectMessageRepository;
import com.codeit.sb13.playlist_for_everyone.global.exception.conversation.DirectMessageNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
@DisplayName("DM 단건 조회")
class DirectMessageReaderTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    @InjectMocks
    private DirectMessageReader directMessageReader;
    private final UUID directMessageId = UUID.randomUUID();

    @Test
    @DisplayName("메시지가 존재하면 상세 조회한 메시지를 반환한다.")
    void getById_returnsMessageWhenFound(){
        DirectMessage message = mock(DirectMessage.class);

        given(directMessageRepository.findDetailedById(directMessageId))
                .willReturn(Optional.of(message));

        DirectMessage result = directMessageReader.getById(directMessageId);
        assertThat(result).isEqualTo(message);
        verify(directMessageRepository).findDetailedById(directMessageId);
    }

    @Test
    @DisplayName("메시지가 없으면 조회한 메시지 ID를 포함한 미존재 예외를 발생시킨다.")
    void getById_throwsWhenMessageNotFound(){
        given(directMessageRepository.findDetailedById(directMessageId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> directMessageReader.getById(directMessageId))
                .isInstanceOfSatisfying(DirectMessageNotFoundException.class,
                        e -> assertThat(e.getDetails())
                                .containsEntry("directMessageId", directMessageId));
    }

    @Test
    @DisplayName("메시지 조회 중 DB 오류가 발생하면 원본 예외를 전달한다.")
    void getById_propagatesRepositoryFailure(){
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("메시지 조회 중 DB  연결 실패");

        given(directMessageRepository.findDetailedById(directMessageId))
                .willThrow(exception);
        assertThatThrownBy(() -> directMessageReader.getById(directMessageId))
                .isSameAs(exception);
        verify(directMessageRepository).findDetailedById(directMessageId);
    }

}
