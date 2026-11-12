# 3. 조회와 응답 변환

기존 대화방을 반환할 때는 실제 최신 메시지와 요청자 기준 미읽음 상태를 사용합니다.
조회 결과를 준비하는 역할과 응답·프로필 이미지 URL을 만드는 역할을 분리했습니다.

## 서로 다른 두 입력 객체

| 입력 | 보관하는 값 | 의미 |
| --- | --- | --- |
| `ConversationCreateCommand` | 요청자 ID, 상대방 ID | 요청 방향을 유지하는 서비스 입력 |
| `ConversationCommand` | 조회한 User 두 개 | 엔티티를 생성할 때 저장 순서로 정렬하는 입력 |

서비스 입력은 인증과 요청 검증을 거친 유효한 ID를 전제로 합니다.
저장용 입력은 참여자 null을 방어하고 두 사용자를 정렬하며, 사용자에게 ID가 부여되어 있어야 합니다.
정렬 후에도 요청자 ID는 따로 유지하여 상대방과 미읽음 여부를 계산합니다.

## 대화방 조회 결과

`ConversationReader`는 정렬된 사용자 쌍과 원래 요청자 ID를 Repository에 전달합니다.
`findDetailByUserPair()`는 다음 값을 `ConversationDetailProjection`으로 반환합니다.

| 쿼리 별칭 | Projection | 의미 |
| --- | --- | --- |
| `conversation` | `getConversation()` | 사용자 쌍에 해당하는 대화방 |
| `lastMessage` | `getLastMessage()` | 최신 메시지 또는 null |
| `hasUnread` | `getHasUnread()` | 요청자가 받은 미읽음 메시지 존재 여부 |

대화방이 없으면 빈 Optional입니다.
대화방은 있지만 메시지가 없으면 Projection은 존재하고, 메시지만 null이며 미읽음 여부는 false입니다.

## 최신 메시지를 선택하는 방법

메시지는 LEFT JOIN으로 연결하여 메시지가 없는 대화방도 결과에 남깁니다.
현재 메시지보다 더 최신인 메시지가 없는 행을 `NOT EXISTS`로 선택합니다.

1. `createdAt`이 더 크면 최신 메시지입니다.
2. 생성 시각이 같으면 ID가 더 큰 메시지를 선택합니다.

결과적으로 `created_at DESC, id DESC`의 첫 메시지를 고르는 기준입니다.
ID는 생성 시각이 같을 때 순서를 결정하기 위한 값이며, ID 크기만으로 시간 순서를 판단하지 않습니다.

## 미읽음 여부는 별도로 계산

`hasUnread`는 같은 대화방에서 **receiver가 요청자이고 read가 false인 메시지가 하나라도 있는지** 확인합니다.

| 상황 | 요청자의 `hasUnread` |
| --- | --- |
| 메시지 없음 | false |
| 요청자가 받은 메시지를 모두 읽음 | false |
| 요청자가 보낸 메시지만 상대방이 읽지 않음 | false |
| 최신 메시지는 읽었지만 이전 수신 메시지가 미읽음 | true |

최신 메시지를 조회하거나 대화방 생성 API를 호출하는 것 자체가 읽음 상태를 변경하지는 않습니다.

## 사용자·프로필 정보를 준비하는 두 경로

기존 대화방 조회는 EntityGraph로 `userA`, `userB`와 각 `profileImage`를 함께 가져옵니다.
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
| 기존 대화방의 Projection | 조회한 최신 메시지와 미읽음 여부 유지 |

요청자가 `userA`이면 `userB`를, 요청자가 `userB`이면 `userA`를 `with`에 넣습니다.
이 변환은 요청자가 해당 대화방의 참여자라는 전제이며, 현재 조회·생성 경로가 그 사용자 쌍으로 대화방을 선택합니다.

`DirectMessageMapper`는 null 입력을 그대로 null로 반환합니다.
메시지가 있으면 실제 발신자·수신자의 사용자 요약을 만들어 `MessageDto`로 변환합니다.
이 타입은 공식 명세의 `DirectMessageDto`와 같은 JSON 필드를 제공합니다.

`UserSummaryMapper`는 이미지가 없으면 URL을 null로 반환하고, 있으면 파일 Key로 `FileStorage`에 URL 생성을 요청합니다.
따라서 필요한 연관관계는 미리 조회되어 있어야 하며, URL 생성 과정에서는 별도의 오류가 발생할 수 있습니다.
저장 커밋 후 실패 시 정책은 [생성과 트랜잭션](02-creation-transactions.md)에서 설명합니다.

## 관련 코드

[조회 쿼리](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/repository/ConversationRepository.java) ·
[Projection](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/dto/repository/ConversationDetailProjection.java) ·
[사용자 조회 컴포넌트](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/service/impl/ConversationUserReader.java) ·
[대화방 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/mapper/ConversationMapper.java) ·
[메시지 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/conversation/mapper/DirectMessageMapper.java) ·
[사용자 요약 Mapper](../../src/main/java/com/codeit/sb13/playlist_for_everyone/domain/user/mapper/UserSummaryMapper.java)

---

[← 생성과 트랜잭션](02-creation-transactions.md) · [목차](README.md) · [다음: 테스트 읽기와 실행 →](04-testing.md)
