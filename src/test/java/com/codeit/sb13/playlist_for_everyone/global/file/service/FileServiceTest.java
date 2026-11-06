package com.codeit.sb13.playlist_for_everyone.global.file.service;

import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileDeleteException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.event.FileDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.global.file.event.FileDeleteEventListener;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("File 서비스 테스트")
public class FileServiceTest {

    @Mock
    private FileInfoRepository fileInfoRepository;

    @Mock
    private FileStorage fileStorage;

    @Mock
    private FileDeleteEventListener listener;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FileService fileService;

    @Test
    @DisplayName("파일 업로드 성공 시 S3에 업로드하고 파일 정보를 저장한다.")
    void save_upload_success() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image".getBytes(StandardCharsets.UTF_8)
        );

        String key = UUID.randomUUID() + ".png";

        given(fileStorage.upload(file)).willReturn(key);

        given(fileInfoRepository.saveAndFlush(any(FileInfo.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // when
            FileInfo fileInfo = fileService.upload(file);

            // then
            assertThat(fileInfo.getKey()).isEqualTo(key);
            assertThat(fileInfo.getOriginalFilename()).isEqualTo("test.png");
            assertThat(fileInfo.getContentType()).isEqualTo("image/png");
            assertThat(fileInfo.getSize()).isEqualTo(file.getSize());

            verify(fileStorage).upload(file);
            verify(fileInfoRepository).saveAndFlush(any(FileInfo.class));

            // 트랜잭션 커밋 상황 재현
            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                );
            }

            // 정상 커밋 시 S3 파일 삭제하지 않음
            verify(fileStorage, never()).delete(key);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("파일 정보 저장 실패 시 롤백되면 S3 업로드 파일을 삭제한다.")
    void upload_saveFailed_deleteS3() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image".getBytes(StandardCharsets.UTF_8)
        );

        String key = "test-key.png";

        given(fileStorage.upload(file)).willReturn(key);

        given(fileInfoRepository.saveAndFlush(any(FileInfo.class)))
                .willThrow(new RuntimeException("DB 저장 실패"));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // when & then
            assertThatThrownBy(() -> fileService.upload(file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB 저장 실패");

            verify(fileStorage).upload(file);

            // 트랜잭션 롤백 상황 재현
            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );
            }

            // 롤백 후 S3 파일 삭제 확인
            verify(fileStorage).delete(key);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("파일 조회 URL 생성 성공")
    void getFileInfo_success() {
        // given
        UUID fileId = UUID.randomUUID();
        String key = "test-image.png";
        String expectedUrl = "https://example.com/test-image.png";

        FileInfo fileInfo = mock(FileInfo.class);

        given(fileInfoRepository.findById(fileId))
                .willReturn(Optional.of(fileInfo));

        given(fileInfo.getKey()).willReturn(key);

        given(fileStorage.getFileUrl(key)).willReturn(expectedUrl);

        // when
        String result = fileService.getFileUrl(fileId);

        // then
        assertThat(result).isEqualTo(expectedUrl);

        verify(fileInfoRepository).findById(fileId);
        verify(fileStorage).getFileUrl(key);
    }

    @Test
    @DisplayName("파일 삭제 시 DB 파일 정보를 삭제하고 S3 삭제 이벤트를 발행한다.")
    void delete_publishesEvent() {
        // given
        UUID id = UUID.randomUUID();
        String key = "test-key.png";

        FileInfo fileInfo = FileInfo.createS3File(
                key,
                "test.png",
                "image/png",
                40L
        );

        given(fileInfoRepository.findById(id)).willReturn(Optional.of(fileInfo));

        // when
        fileService.delete(id);

        // then
        verify(fileInfoRepository).delete(fileInfo);

        // 이벤트 객체 캡처
        ArgumentCaptor<FileDeleteEvent> captor =
                ArgumentCaptor.forClass(FileDeleteEvent.class);

        // 발행한 이벤트 캡처
        verify(eventPublisher).publishEvent(captor.capture());

        // 이벤트에 담긴 key 검증
        assertThat(captor.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("존재하지 않는 파일 삭제 시 예외가 발생하고 삭제 이벤트를 발행하지 않는다.")
    void delete_fileNotFound() {
        // given
        UUID id = UUID.randomUUID();
        given(fileInfoRepository.findById(id))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> fileService.delete(id))
                .isInstanceOf(FileNotFoundException.class);

        verify(fileInfoRepository, never()).delete(any(FileInfo.class));
        verifyNoInteractions(eventPublisher);
    }

}
