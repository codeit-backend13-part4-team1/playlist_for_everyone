package com.codeit.sb13.playlist_for_everyone.global.file.repository;

import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FileInfoRepository extends JpaRepository<FileInfo, UUID> {
}
