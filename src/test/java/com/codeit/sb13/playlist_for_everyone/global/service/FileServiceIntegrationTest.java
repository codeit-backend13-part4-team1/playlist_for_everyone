package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("FileService 트랜잭션 통합 테스트")
public class FileServiceIntegrationTest {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileInfoRepository fileInfoRepository;

    @Autowired
    private PlatformTransactionManager tm;

    @MockitoBean
    private FileStorage fileStorage;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(tm);
    }

    @Test
    @DisplayName("DB 커밋 후 S3 파일을 삭제한다")
    void delete_commit() {
        // given
        FileInfo fileInfo = fileInfoRepository.saveAndFlush(
                FileInfo.createS3File(
                        UUID.randomUUID() + ".png",
                        "test.png",
                        "image/png",
                        40L
                )
        );

        UUID fileId = fileInfo.getId();
        String key = fileInfo.getKey();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            fileService.delete(fileId);

            // 커밋 전에는 S3 삭제가 호출되지 않음
            verify(fileStorage, never()).delete(key);
        });

        // then
        assertThat(fileInfoRepository.findById(fileId)).isEmpty();
        verify(fileStorage).delete(key);
    }

    @Test
    @DisplayName("DB 롤백 시 S3 파일을 삭제하지 않는다")
    void delete_rollback() {
        // given
        FileInfo fileInfo = fileInfoRepository.saveAndFlush(
                FileInfo.createS3File(
                        UUID.randomUUID() + ".png",
                        "test.png",
                        "image/png",
                        40L
                )
        );

        UUID fileId = fileInfo.getId();
        String key = fileInfo.getKey();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            fileService.delete(fileId);
            status.setRollbackOnly();
        });

        // then
        assertThat(fileInfoRepository.findById(fileId)).isPresent();
        verify(fileStorage, never()).delete(key);
    }

}
