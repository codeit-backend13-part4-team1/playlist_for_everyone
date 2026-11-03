package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("파일 삭제 후 트랜잭션 커밋 시 S3 파일도 삭제한다")
    void delete_success() {
        // given
        UUID id = UUID.randomUUID();
        String key = "test-key.png";

        FileInfo fileInfo = FileInfo.createS3File(
                key,
                "test.png",
                "image/png",
                40L
        );

        given(fileInfoRepository.findById(id))
                .willReturn(Optional.of(fileInfo));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // when
            fileService.delete(id);

            // then
            verify(fileInfoRepository).delete(fileInfo);

            // 커밋 전에는 S3 파일을 삭제하지 않음
            verify(fileStorage, never()).delete(key);

            // 트랜잭션 커밋 상황 재현
            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            // 커밋 후 S3 파일 삭제 확인
            verify(fileStorage).delete(key);

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("존재하지 않는 파일 삭제 시 FileNotFoundException이 발생한다")
    void delete_fileNotFound() {
        // given
        UUID id = UUID.randomUUID();
        given(fileInfoRepository.findById(id))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> fileService.delete(id))
                .isInstanceOf(FileNotFoundException.class);

        verify(fileStorage, never()).delete(anyString());
        verify(fileInfoRepository, never()).delete(any(FileInfo.class));
    }

}
