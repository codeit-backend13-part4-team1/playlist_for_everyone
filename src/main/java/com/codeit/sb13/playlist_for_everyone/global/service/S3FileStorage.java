package com.codeit.sb13.playlist_for_everyone.global.service;

import com.codeit.sb13.playlist_for_everyone.global.config.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

@Service
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public String upload(MultipartFile file) {
        return "";
    }

    @Override
    public void delete(String key) {

    }
}
