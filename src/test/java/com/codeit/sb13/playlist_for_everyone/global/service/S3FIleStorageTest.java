package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.config.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("S3FileStorage 테스트")
@ExtendWith(MockitoExtension.class)
public class S3FIleStorageTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    private S3FileStorage s3FileStorage;

    @BeforeEach
    void setUp() {
        S3Properties props = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                ""
        );

        s3FileStorage = new S3FileStorage(s3Client, props);
    }

    @Test
    @DisplayName("파일 업로드 성공")
    void upload_success() {
        // given
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image".getBytes(StandardCharsets.UTF_8)
        );

        // when
        String key = s3FileStorage.upload(file);

        // then
        assertThat(key).isNotBlank();

        verify(s3Client, times(1))
                .putObject(
                        any(PutObjectRequest.class),
                        any(RequestBody.class)
                );
    }

    @Test
    @DisplayName("파일 삭제 성공")
    void delete_success() {
        // given
        String key = "test-image.png";

        // when
        s3FileStorage.delete(key);

        // then
        verify(s3Client, times(1))
                .deleteObject(any(DeleteObjectRequest.class));
    }

}
