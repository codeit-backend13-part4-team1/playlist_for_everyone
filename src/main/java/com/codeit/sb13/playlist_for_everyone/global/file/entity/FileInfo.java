package com.codeit.sb13.playlist_for_everyone.global.file.entity;

import com.codeit.sb13.playlist_for_everyone.global.domain.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "file_infos")
public class FileInfo extends CreatedAtEntity {

    String key;

    @Column(name = "original_filename")
    String originalFilename;

    @Column(name = "content_type")
    String contentType;

    Long size;

    public static FileInfo createS3File(
            String key,
            String originalFilename,
            String contentType,
            long size
    ) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.key = key;
        fileInfo.originalFilename = originalFilename;
        fileInfo.contentType = contentType;
        fileInfo.size = size;
        return fileInfo;
    }

}
