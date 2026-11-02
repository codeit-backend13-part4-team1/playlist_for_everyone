package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.file.config.S3Properties;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileDeleteException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileEmptyException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileInvalidTypeException;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.S3FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("S3FileStorage 테스트")
@ExtendWith(MockitoExtension.class)
public class S3FileStorageTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3FileStorage s3FileStorage;

    @BeforeEach
    void setUp() {
        S3Properties props = new S3Properties(
                BUCKET,
                "ap-northeast-2",
                ""
        );

        s3FileStorage = new S3FileStorage(s3Client, props, s3Presigner);
    }

    @Test
    @DisplayName("파일 업로드 성공")
    void upload_success() throws IOException {
        // given
        MultipartFile file = spy(new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image".getBytes(StandardCharsets.UTF_8)
        ));

        // when
        String key = s3FileStorage.upload(file);

        // then
        assertThat(key).isNotBlank();

        verify(s3Client, times(1))
                .putObject(
                        any(PutObjectRequest.class),
                        any(RequestBody.class)
                );

        // 스트림 방식으로 업로드하는지 검증
        verify(file).getInputStream();
        verify(file, never()).getBytes();
    }

    @Test
    @DisplayName("빈 파일 업로드 시 예외가 발생한다.")
    void upload_emptyFile() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        // when & then
        assertThatThrownBy(() -> s3FileStorage.upload(file))
                .isInstanceOf(FileEmptyException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식 업로드 시 예외가 발생한다.")
    void upload_invalidType() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream",
                "test".getBytes(StandardCharsets.UTF_8)
        );

        // when & then
        assertThatThrownBy(() -> s3FileStorage.upload(file))
                .isInstanceOf(FileInvalidTypeException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("파일 조회 URL 생성 성공")
    void getFileUrl_success() throws IOException {
        // given
        String key = "test-image.png";
        String expectedUrl = "https://example.com/test-image.png";

        PresignedGetObjectRequest presignedRequest =
                mock(PresignedGetObjectRequest.class);

        when(presignedRequest.url())
                .thenReturn(URI.create(expectedUrl).toURL());

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        // when
        String result = s3FileStorage.getFileUrl(key);

        // then
        assertThat(result).isEqualTo(expectedUrl);

        verify(s3Presigner).presignGetObject(
                any(GetObjectPresignRequest.class)
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
        verify(s3Client).deleteObject(
                argThat((DeleteObjectRequest request) ->
                        BUCKET.equals(request.bucket())
                                && key.equals(request.key())
                )
        );
    }

    @Test
    @DisplayName("빈 key로 삭제 요청 시 예외가 발생한다.")
    void delete_emptyKey() {
        // given
        String key = "";

        // when & then
        assertThatThrownBy(() -> s3FileStorage.delete(key))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("S3 파일 삭제 실패 시 예외 발생")
    void delete_failed() {
        // given
        String key = "test-image.png";

        doThrow(S3Exception.builder()
                .message("S3 삭제 실패")
                .build())
                .when(s3Client)
                .deleteObject(any(DeleteObjectRequest.class));

        // when & then
        assertThatThrownBy(() -> s3FileStorage.delete(key))
                .isInstanceOf(FileDeleteException.class);
    }

    @Test
    @DisplayName("클라이언트 MIME 타입과 관계없이 확장자에 맞는 MIME 타입으로 업로드한다")
    void upload_contentType() throws IOException {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "text/html",
                "test image".getBytes()
        );

        // when
        s3FileStorage.upload(file);

        // then
        ArgumentCaptor<PutObjectRequest> captor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                captor.capture(),
                any(RequestBody.class)
        );

        assertThat(captor.getValue().contentType())
                .isEqualTo("image/png");
    }

}
