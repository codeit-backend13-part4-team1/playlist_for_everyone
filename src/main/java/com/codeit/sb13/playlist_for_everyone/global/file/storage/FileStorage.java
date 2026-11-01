package com.codeit.sb13.playlist_for_everyone.global.file.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    String upload(MultipartFile file);

    void delete(String key);

}