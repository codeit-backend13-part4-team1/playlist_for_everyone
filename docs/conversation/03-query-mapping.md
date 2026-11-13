# 조회와 응답 변환

생성 시 기존 대화방을 반환하거나 조회 API를 호출할 때는 실제 최신 메시지와 요청자 기준 미읽음 상태를 사용합니다.
조회 결과를 준비하는 역할과 응답·프로필 이미지 URL을 만드는 역할을 분리했습니다.

## 생성 입력과 조회 조건

| 입력 | 보관하는 값 | 의미 |
| --- | --- | --- |
| `ConversationCreateCommand` | 요청자 ID, 상대방 ID | 요청 방향을 유지하는 서비스 입력 |
| `ConversationCommand` | 조회한 User 두 개 | 엔티티를 생성할 때 저장 순서로 정렬하는 입력 |
| `ConversationFindCondition` | 요청자 ID, 상대방 ID | 상대방 기준 조회 입력 |
| `ConversationFindByIdCondition` | 요청자 ID, 대화방 ID | 대화방 식별과 참여자 제한에 사용하는 입력 |
| `ConversationSearchCondition` | 요청자 ID, 검색·정렬·커서 값 | 목록과 전체 개수 조회에 공유하는 입력 |

HTTP 요청의 필수 값·형식 검증과 내부 조회 조건의 역할을 구분합니다.
예를 들어 검색 조건의 생성자는 필수 필드의 null만 방어하므로 다른 호출자도 [입력 계약](05-search-pagination.md#목록-요청-계약)을 지켜야 합니다.
사용자 쌍 조회는 `ConversationPair`가 UUID 문자열을 정렬하고 요청자 ID를 따로 유지합니다.
저장용 `ConversationCommand`도 같은 정렬 규칙을 사용하며, 사용자에게 ID가 부여되어 있어야 합니다.

## 대화방 조회 결과

`ConversationReader`가 읽기 트랜잭션을 열고 사용자 정의 Repository에 조회를 위임합니다.
Querydsl의 `Projections.constructor()`가 아래 값을 `ConversationDetailResult`로 묶습니다.
이 record는 `ConversationDetailProjection` 계약을 구현하며, HTTP DTO가 아닌 엔티티 참조를 보관합니다.

| 결과 필드 | Projection | 의미 |
| --- | --- | --- |
| `conversation` | `getConversation()` | 조회 조건에 해당하는 대화방 |
| `lastMessage` | `getLastMessage()` | 최신 메시지 또는 null |
| `hasUnread` | `getHasUnread()` | 요청자가 받은 미읽음 메시지 존재 여부 |

단건·사용자 쌍 조회에 결과가 없으면 빈 Optional이며 서비스가 예외 여부를 결정합니다.
단건 조회는 ID와 참여자 조건을 함께 적용하고, 사용자 쌍 조회에는 요청자와 상대방으로 구성한 쌍을 전달해야 합니다.
대화방은 있지만 메시지가 없으면 Projection은 존재하고, 메시지만 null이며 미읽음 여부는 false입니다.

## 최신 메시지를 선택하는 방법

공통 조회 쿼리는 메시지를 LEFT JOIN으로 연결하여 메시지가 없는 대화방도 결과에 남깁니다.
최신 메시지 조건을 WHERE가 아닌 JOIN의 ON에 두어 이 성질을 유지합니다.
현재 메시지보다 더 최신인 메시지가 없는 행을 `NOT EXISTS`로 선택합니다.

1. `createdAt`이 더 크면 최신 메시지입니다.
2. 생성 시각이 같으면 ID가 더 큰 메시지를 선택합니다.

결과적으로 `created_at DESC, id DESC`의 첫 메시지를 고르는 기준입니다.
ID는 생성 시각이 같을 때 순서를 결정하기 위한 값이며, ID 크기만으로 시간 순서를 판단하지 않습니다.
최신 메시지 선택은 목록의 검색 조건·정렬 방향과 별개입니다.

## 미읽음 여부는 별도로 계산

`hasUnread`는 같은 대화방에서 **receiver가 요청자이고 read가 false인 메시지가 하나라도 있는지** 확인합니다.

| 상황 | 요청자의 `hasUnread` |
| --- | --- |
| 메시지 없음 | false |
| 요청자가 받은 메시지를 모두 읽음 | false |
| 요청자가 보낸 메시지만 상대방이 읽지 않음 | false |
| 최신 메시지는 읽었지만 이전 수신 메시지가 미읽음 | true |

생성·조회 API 호출 자체가 읽음 상태를 변경하지는 않습니다.

## 사용자·프로필 정보를 준비하는 두 경로

대화방 조회는 Querydsl의 LEFT fetch join으로 `userA`, `userB`와 각 `profileImage`를 함께 가져옵니다.
메시지의 발신자·수신자가 이 두 참여자인 경우 같은 영속성 컨텍스트의 사용자 정보를 사용할 수 있습니다.
저장소 테스트는 이 조건에서 메시지 발신자·수신자의 프로필에 접근해도 SQL이 추가되지 않는지 확인합니다.

신규 저장은 `ConversationUserReader → UserService.getById()`로 사용자를 조회합니다.
현재 `UserService`는 프로필 이미지 정보도 함께 가져오며, 이 시점에는 이미지 URL을 만들지 않습니다.

`ConversationUserReader`는 사용자 도메인에 대한 간단한 조회 진입점을 제공하는 **파사드 역할**입니다.
기존 서비스 계층 안에서 호출 지점을 모은 구조로, 별도 포트 인터페이스는 없습니다.
User 엔티티를 반환하므로 사용자 엔티티 의존성은 남아 있고, 상대방 미존재 예외 변환은 Saver가 담당합니다.

## 응답 변환

| Mapper 입력 | 응답 구성 |
| --- | --- |
| 신규 저장한 `Conversation` | 상대방 정보와 `lastestMessage: null`, `hasUnread: false` |
| 기존 대화방·조회 API의 Projection | 조회한 최신 메시지와 미읽음 여부 유지 |

요청자가 `userA`이면 `userB`를, 요청자가 `userB`이면 `userA`를 `with`에 넣습니다.
이 변환은 요청자가 참여자라는 전제입니다. 생성·상대방 기준 조회는 사용자 쌍으로,
목록·단건 조회는 Repository의 참여자 조건으로 대상을 제한합니다.

`DirectMessageMapper`는 null 입력을 그대로 null로 반환합니다.
메시지가 있으면 실제 발신자·수신자의 사용자 요약을 만들어 `MessageDto`로 변환합니다.
이 타입은 공식 명세의 `DirectMessageDto`와 같은 JSON 필드를 제공합니다.

`UserSummaryMapper`는 이미지가 없으면 URL을 null로 반환하고, 있으면 파일 Key로 `FileStorage`에 URL 생성을 요청합니다.
따라서 필요한 연관관계는 미리 조회되어 있어야 하며, URL 생성 과정에서는 별도의 오류가 발생할 수 있습니다.
상위 트랜잭션이 없는 HTTP 호출에서는 Reader 반환 또는 Saver 커밋 후에 URL을 생성합니다.
상위 트랜잭션이 있으면 Reader가 참여하므로 이 조건을 모든 호출에 일반화하지 않습니다.
URL 생성 실패는 오류로 전달하며, 이미 커밋한 신규 저장은 유지됩니다. 자세한 경계는 [생성과 트랜잭션](02-creation-transactions.md)을 참고합니다.

## 관련 코드

[Querydsl 조회 구현](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/impl/ConversationCustomRepositoryImpl.java) ·
[Projection](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/dto/repository/ConversationDetailProjection.java) ·
[조회 결과 구현체](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/dto/repository/ConversationDetailResult.java) ·
[사용자 조회 컴포넌트](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/impl/ConversationUserReader.java) ·
[대화방 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/mapper/ConversationMapper.java) ·
[메시지 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/mapper/DirectMessageMapper.java) ·
[사용자 요약 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/user/mapper/UserSummaryMapper.java)

---

[← 생성과 트랜잭션](02-creation-transactions.md) · [목차](README.md) · [다음: 검색과 커서 페이지 처리 →](05-search-pagination.md)
