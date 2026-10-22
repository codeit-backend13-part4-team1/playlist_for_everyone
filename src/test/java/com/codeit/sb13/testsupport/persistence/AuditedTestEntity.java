package com.codeit.sb13.testsupport.persistence;

import com.codeit.sb13.playlist_for_everyone.global.domain.UpdatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 운영 엔티티 스캔 범위 밖에 두고 JPA 테스트에서만 명시적으로 등록한다.
@Entity
@Table(name = "audited_test_entity")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditedTestEntity extends UpdatedAtEntity {

    @Column(nullable = false)
    private String name;

    public AuditedTestEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
