package com.codeit.sb13.playlist_for_everyone.global.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@MappedSuperclass
@Getter
@ToString
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
