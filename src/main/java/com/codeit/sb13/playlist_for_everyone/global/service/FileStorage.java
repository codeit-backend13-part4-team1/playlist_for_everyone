package com.codeit.sb13.playlist_for_everyone.global.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    String upload(MultipartFile file);

    void delete(String key);

}