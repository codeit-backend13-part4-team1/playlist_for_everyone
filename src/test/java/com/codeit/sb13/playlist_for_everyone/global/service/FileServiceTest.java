package com.codeit.sb13.playlist_for_everyone.global.service;

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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        given(fileInfoRepository.save(any(FileInfo.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        FileInfo fileInfo = fileService.upload(file);

        // then
        assertThat(fileInfo.getKey()).isEqualTo(key);
        assertThat(fileInfo.getOriginalFilename()).isEqualTo("test.png");
        assertThat(fileInfo.getContentType()).isEqualTo("image/png");
        assertThat(fileInfo.getSize()).isEqualTo(file.getSize());

        verify(fileStorage).upload(file);
        verify(fileInfoRepository).save(any(FileInfo.class));
    }

    @Test
    @DisplayName("파일 정보 저장 실패 시 S3에 업로드 파일을 삭제한다.")
    void upload_saveFailed_deleteS3() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test image".getBytes()
        );

        String key = "test-key.png";

        given(fileStorage.upload(file)).willReturn(key);

        given(fileInfoRepository.save(any(FileInfo.class)))
                .willThrow(new RuntimeException("DB 저장 실패"));

        // when & then
        assertThatThrownBy(() -> fileService.upload(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        verify(fileStorage).upload(file);
        verify(fileStorage).delete(key);
    }

}
