package com.codeit.sb13.playlist_for_everyone.global.file.service;

import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileDeleteException;
import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileInfoRepository fileInfoRepository;
    private final FileStorage fileStorage;

    @Transactional
    public FileInfo upload(MultipartFile file) {
        String key = fileStorage.upload(file);

        FileInfo fileInfo = FileInfo.createS3File(
                key,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );

        try {
            return fileInfoRepository.save(fileInfo);
        } catch (RuntimeException e) {
            try {
                fileStorage.delete(key);    // 고아 파일 제거
            } catch (RuntimeException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }

    @Transactional
    public void delete(UUID id) {
        FileInfo fileInfo = fileInfoRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException(Map.of()));

        // S3 삭제
        fileStorage.delete(fileInfo.getKey());

        // FileInfo 삭제
        fileInfoRepository.delete(fileInfo);

    }

}
