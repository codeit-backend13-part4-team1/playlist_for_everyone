package com.codeit.sb13.playlist_for_everyone.global.file.service;

import com.codeit.sb13.playlist_for_everyone.global.exception.file.FileNotFoundException;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.event.FileDeleteEvent;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private final FileInfoRepository fileInfoRepository;
    private final FileStorage fileStorage;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FileInfo upload(MultipartFile file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("트랜잭션이 활성화되지 않았습니다.");
        }

        String key = fileStorage.upload(file);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                fileStorage.delete(key);
                            } catch (RuntimeException e) {
                                log.error("트랜잭션 롤백 후 S3 파일 삭제 실패. key={}", key, e);
                            }
                        } else if (status == STATUS_UNKNOWN) {
                            log.warn("트랜잭션 완료 상태 확인 불가. S3 파일 확인 필요. key={}", key);
                        }
                    }
                }
        );

        FileInfo fileInfo = FileInfo.createS3File(
                key,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );

        return fileInfoRepository.saveAndFlush(fileInfo);
    }

    @Transactional(readOnly = true)
    public String getFileUrl(UUID fileId) {
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(
                        Map.of("fileId", fileId)
                ));

        return fileStorage.getFileUrl(fileInfo.getKey());
    }

    @Transactional
    public void delete(UUID id) {
        FileInfo fileInfo = fileInfoRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException(Map.of("fileId", id)));

        String key = fileInfo.getKey();

        // DB 파일 정보 삭제
        fileInfoRepository.delete(fileInfo);

        // DB 커밋 후 S3 파일 삭제를 위한 이벤트 발행
        eventPublisher.publishEvent(new FileDeleteEvent(key));
    }

}
