package com.codeit.sb13.playlist_for_everyone.global.file.storage;

import com.codeit.sb13.playlist_for_everyone.global.file.config.S3Properties;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".bmp", "image/bmp"
    );

    @Override
    public String upload(MultipartFile file) {
        // 빈 파일 검증
        if (file == null || file.isEmpty()) {
            throw new FileEmptyException(Map.of());
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);

        // 확장자 검증
        if (extension == null ||
                !ALLOWED_CONTENT_TYPES.containsKey("." + extension.toLowerCase(Locale.ROOT))) {
            throw new FileInvalidTypeException(
                    Map.of("filename",
                            originalFilename == null ? "unknown" : originalFilename)
            );
        }

        String normalizedExtension = "." + extension.toLowerCase(Locale.ROOT);

        if (!ALLOWED_CONTENT_TYPES.containsKey(normalizedExtension)) {
            throw new FileInvalidTypeException(
                    Map.of("filename", originalFilename)
            );
        }

        // S3 파일 key 생성
        String key = UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(ALLOWED_CONTENT_TYPES.get("." + extension.toLowerCase(Locale.ROOT)))
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );

            log.info("S3 파일 업로드 완료: {}", key);
            return key;

        } catch (IOException | SdkException e) {
            log.error("S3 파일 업로드 실패. key={}", key, e);

            throw new FileUploadException(
                    Map.of("key", key),
                    e
            );
        }

    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("파일 key가 비어 있습니다.");
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .build();

        try {
            s3Client.deleteObject(request);

            log.info("S3 파일 삭제 완료: {}", key);

        } catch (SdkException e) {
            log.error("S3 파일 삭제 실패. key={}", key, e);

            throw new FileDeleteException(
                    Map.of("key", key),
                    e
            );
        }

    }

}
