package com.codeit.sb13.playlist_for_everyone.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners( AuditingEntityListener.class)
@Getter
@ToString(callSuper = true)
public class UpdatedAtEntity extends CreatedAtEntity{

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
