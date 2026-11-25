package com.codeit.sb13.playlist_for_everyone.domain.content.document;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "contents")
// 검색용 데이터 모델
public class ContentDocument {

    // 정확한 식별자 조회
    @Id
    @Field(type = FieldType.Keyword)
    private UUID id;

    // 정확한 유형 필터링
    @Field(type = FieldType.Keyword)
    private String type;

    // 검색
    @Field(type = FieldType.Text)
    private String title;

    // 검색
    @Field(type = FieldType.Text)
    private String description;

    // 정확한 태그 필터링
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    // 생성일 정렬 및 커서
    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

}
