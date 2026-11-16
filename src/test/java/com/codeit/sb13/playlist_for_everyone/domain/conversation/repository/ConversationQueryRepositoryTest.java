package com.codeit.sb13.playlist_for_everyone.domain.conversation.repository;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.command.ConversationCommand;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationDetailProjection;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationFindByIdCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.dto.repository.ConversationSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.Conversation;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.DirectMessage;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.rmi.server.UID;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.generate_statistics=true"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Testcontainers
@DisplayName("대화방 조회 리포지토리")
public class ConversationQueryRepositoryTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("요청자가 참여자 A 또는 B이면 해당 대화방의 상세 정보를 조회한다.")
    void findDetailById_returnsConversationForEitherParticipant(boolean requesterIsUserA){
        User firstUser = persistUser("first@example.com", "첫 번째 사용자");
        User secondUser = persistUser("second@example.com", "두 번째 사용자");

        ConversationCommand pair = new ConversationCommand(firstUser, secondUser);

        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        UUID conversationId = saved.getId();
        UUID requesterId = requesterIsUserA ? pair.userA().getId()
                : pair.userB().getId();
        entityManager.clear();

        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, conversationId);
        Optional<ConversationDetailProjection> result = conversationRepository.findDetailById(condition);
        assertThat(result).hasValueSatisfying(projection ->{
            assertThat(projection.getConversation().getId())
                    .isEqualTo(conversationId);
            assertThat(projection.getLastMessage()).isNull();
            assertThat(projection.getHasUnread()).isFalse();
        });
    }

    @ParameterizedTest(name = "대화방 존재={0}, 요청자 참여={1}")
    @CsvSource({
            "false, true",
            "true, false"
    })
    @DisplayName("대화방이 없거나 요청자가 참여자가아니면 빈 Optional을 반환한다.")
    void findDetailById_returnsEmptyWhenMissingOrNotParticipant(
            boolean conversationExists, boolean requesterIsParticipant
    ){
        User firstUser = persistUser("first@example.com", "첫 번째 사용자");
        User secondUser = persistUser("second@example.com", "두 번째 사용자");
        User outsider = persistUser("outsider@example.com", "외부 사용자");

        ConversationCommand pair = new ConversationCommand(firstUser, secondUser);

        Conversation saved = conversationRepository.saveAndFlush(new Conversation(pair));

        UUID conversationId = conversationExists ? saved.getId() : UUID.randomUUID();
        UUID requesterId = requesterIsParticipant ? pair.userA().getId() : outsider.getId();
        entityManager.clear();

        ConversationFindByIdCondition condition = new ConversationFindByIdCondition(requesterId, conversationId);

        Optional<ConversationDetailProjection> result = conversationRepository.findDetailById(condition);
        assertThat(result).isEmpty();
    }

    @ParameterizedTest(name = "검색어={0}")
    @NullSource
    @ValueSource(strings = {"검색 대상"})
    @DisplayName("검색어 유무와 관계없이 요청자가 참여한 대화방만 목록과 전체 개수에 포함한다.")
    void searchAndSearchCount_includeOnlyRequesterConversations(String keywordLike){
        List<User> users = Stream.of(
                        persistUser("first@example.com", "검색 대상 1"),
                        persistUser("second@example.com", "검색 대상 2"),
                        persistUser("third@example.com", "검색 대상 3")
                ).sorted(Comparator.comparing(user -> user.getId().toString()))
                .toList();
        User lowerUser = users.get(0);
        User requester = users.get(1);
        User higherUser = users.get(2);

        Conversation asUserA = conversationRepository.save(new Conversation(new ConversationCommand(requester, higherUser)));
        Conversation asUserB = conversationRepository.save(new Conversation(new ConversationCommand(lowerUser, requester)));
        conversationRepository.save(new Conversation(new ConversationCommand(lowerUser, higherUser)));
        entityManager.flush();

        UUID requesterId = requester.getId();
        UUID asUserAId = asUserA.getId();
        UUID asUserBId = asUserB.getId();

        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(
                keywordLike,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        long totalCount = conversationRepository.searchCount(condition);

        assertThat(result).extracting(projection ->
                projection.getConversation().getId())
                .containsExactlyInAnyOrder(asUserAId, asUserBId);
        assertThat(totalCount).isEqualTo(2L);
    }

    @ParameterizedTest(name = "[{index}] 검색어={0}, 예상 상대방={1}")
    @MethodSource("counterpartNameSearchCases")
    @DisplayName("상대방 이름 검색에 대소문자·공백 규칙을 적용하고 목록과 전체 개수를 일치시키다.")
    void searchAndSearchCount_filterByCounterpartName(String  keywordLike, List<String> expectedNames){
        List<User> users = Stream.of(
                        persistUser("first@example.com", "사용자 1"),
                        persistUser("second@example.com", "사용자 2"),
                        persistUser("third@example.com", "사용자 3")
                ).sorted(Comparator.comparing(user -> user.getId().toString()))
                .toList();

        User alice = users.get(0);
        User requester = users.get(1);
        User bob = users.get(2);

        alice.updateName("Alice");
        requester.updateName("Requester");
        bob.updateName("Bob");

        Conversation aliceConversation = conversationRepository.save(new Conversation(new ConversationCommand(alice, requester)));
        Conversation bobConversation = conversationRepository.save(new Conversation(new ConversationCommand(requester, bob)));

        entityManager.flush();
        UUID requesterId = requester.getId();
        Map<String, UUID> conversationIds = Map.of("Alice", aliceConversation.getId(),
                "Bob", bobConversation.getId());
        List<UUID> expectedIds = expectedNames.stream()
                .map(conversationIds::get)
                .toList();
        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(keywordLike,
                null,
                null,
                10,
                SortDirection.DESCENDING,
                ConversationSortBy.CREATED_AT,
                requesterId);

        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        long totalCount = conversationRepository.searchCount(condition);

        assertThat(result).extracting(projection ->
                projection.getConversation().getId())
                .containsExactlyInAnyOrderElementsOf(expectedIds);

        assertThat(totalCount).isEqualTo(expectedIds.size());

    }

    @Test
    @DisplayName("과거 메서지가 여러건 검색돼도 대화방은 한 번만 반환하고 실제 최신 메시지를 제공한다.")
    void searchAndSearchCount_matchPastMessagesWithoutDuplicatesAndReturnsMessage(){
        User requester = persistUser("requester@example.com", "요청자");
        User alice = persistUser("alice@example.com", "Alice");
        User bob = persistUser("bob@example.com", "Bob");

        Conversation target = conversationRepository.save(new Conversation(new ConversationCommand(requester, alice)));
        conversationRepository.save(new Conversation(new ConversationCommand(requester, bob)));
        Conversation external = conversationRepository.save(new Conversation(new ConversationCommand(alice, bob)));

        entityManager.flush();;
        UUID requesterId = requester.getId();
        UUID conversationId = target.getId();
        UUID latestMessageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 0);

        insertMessage(UUID.randomUUID(), conversationId, requesterId, alice.getId(), "Movie 추천 부탁해",true, createdAt);
        insertMessage(UUID.randomUUID(), conversationId,  alice.getId(), requesterId, "다음 Movie 같이 보자",true, createdAt.plusMinutes(1));
        insertMessage(latestMessageId, conversationId,  alice.getId(), requesterId, "오늘은 여기까지",true, createdAt.plusMinutes(2));
        insertMessage(UUID.randomUUID(), external.getId(), alice.getId(), bob.getId(), "Movie 이야기",true, createdAt.plusMinutes(3));
        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition("  moViE  ", null, null, 10, SortDirection.DESCENDING,
                ConversationSortBy.CREATED_AT, requesterId);

        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        long totalCount = conversationRepository.searchCount(condition);
        assertThat(result).extracting(projection ->
                projection.getConversation().getId())
                .containsExactly(conversationId);
        assertThat(totalCount).isEqualTo(1L);
        ConversationDetailProjection projection = result.get(0);

        assertThat(projection.getLastMessage()).isNotNull();
        assertThat(projection.getLastMessage().getId()).isEqualTo(latestMessageId);
        assertThat(projection.getLastMessage().getContent()).isEqualTo("오늘은 여기까지");
    }

    @ParameterizedTest(name = "검색어={0}, 이름에서 일치={1}")
    @CsvSource({
            "%, true",
            "%, false",
            "_, true",
            "_, false"
    })
    @DisplayName("이름과 메시지 검색에서 퍼센트와 밑줄을 일반 문자로 처리한다.")
    void searchAndSearchCount_treatWildcardCharactersAsLiterals(String keywordLike, boolean matchByName){
        User requester = persistUser("requester@example.com", "요청자");
        User targetUser = persistUser("target@example.com", matchByName ? "생다" + keywordLike + "이름" : "대화 상대");
        User otherUser = persistUser("other@example.com", "일반 사용자");

        Conversation target = conversationRepository.save(new Conversation(new ConversationCommand(requester, targetUser)));
        Conversation other = conversationRepository.save(new Conversation(new ConversationCommand(requester, otherUser)));
        entityManager.flush();

        UUID requesterId = requester.getId();
        UUID targetId = target.getId();
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 0);

        insertMessage(UUID.randomUUID(), targetId, targetUser.getId(), requesterId,
                matchByName ? "일반 메시지" : "내용" + keywordLike +"포함", true, createdAt);
        insertMessage(UUID.randomUUID(), other.getId(), otherUser.getId(), requesterId,
                "일반 메시지", true, createdAt);
        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(keywordLike, null, null, 10, SortDirection.DESCENDING, ConversationSortBy.CREATED_AT, requesterId);
        long totalCount = conversationRepository.searchCount(condition);
        List<ConversationDetailProjection> result = conversationRepository.search(condition);

        assertThat(result).extracting(projection -> projection.getConversation().getId())
                .containsExactly(targetId);
        assertThat(totalCount).isEqualTo(1L);

    }

    @Test
    @DisplayName("대화방별 최신 메시지와 요청자의 미읽음 상태를 구분하고 메시지가 없는 대화방도 반환한다.")
    void search_returnsLatestMessageAndUnreadStatusPerConversation(){
        User requester = persistUser("requester@example.com", "요청자");
        User alice = persistUser("alice@example.com", "Alice");
        User bob = persistUser("bob@example.com", "Bob");
        User charlie = persistUser("charlie@example.com", "Charlie");

        Conversation withAlice = conversationRepository.save(new Conversation(new ConversationCommand(requester, alice)));
        Conversation withBob = conversationRepository.save(new Conversation(new ConversationCommand(requester, bob)));
        Conversation withoutMessages = conversationRepository.save(new Conversation(new ConversationCommand(requester, charlie)));
        entityManager.flush();

        UUID requesterId = requester.getId();
        UUID aliceConversationId = withAlice.getId();
        UUID bobConversationId = withBob.getId();
        UUID emptyConversationId = withoutMessages.getId();

        UUID aliceLatestId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bobLowerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID bobHigherId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 0);

        insertMessage(UUID.fromString("00000000-0000-0000-0000-000000000009"),
                        aliceConversationId, alice.getId(),
                        requesterId,
                        "과거 미읽음 수신 메시지", false,
                        createdAt
                );
        insertMessage(
                aliceLatestId, aliceConversationId,
                alice.getId(), requesterId,
                "최신 읽음 수신 메시지", true,
                createdAt.plusMinutes(1)
        );

        insertMessage(
                bobHigherId, bobConversationId,
                requesterId, bob.getId(),
                "큰 ID의 발신 메시지", false,
                createdAt.plusMinutes(2)
        );

        insertMessage(
                bobLowerId, bobConversationId,
                requesterId, bob.getId(),
                "작은 ID의 발신 메시지", false,
                createdAt.plusMinutes(2)
        );

        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(null, null, null, 10, SortDirection.DESCENDING, ConversationSortBy.CREATED_AT, requesterId);
        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        assertThat(result).extracting(projection -> projection.getConversation().getId())
                .containsExactlyInAnyOrder(aliceConversationId, bobConversationId, emptyConversationId);

        Map<UUID, ConversationDetailProjection> byConversationId = result.stream().collect(Collectors.toMap(projection ->
                        projection.getConversation().getId(),
                projection -> projection));

        ConversationDetailProjection aliceResult = byConversationId.get(aliceConversationId);
        assertThat(aliceResult.getLastMessage()).isNotNull();
        assertThat(aliceResult.getLastMessage().getId()).isEqualTo(aliceLatestId);
        assertThat(aliceResult.getHasUnread()).isTrue();

        ConversationDetailProjection bobResult = byConversationId.get(bobConversationId);
        assertThat(bobResult.getLastMessage()).isNotNull();
        assertThat(bobResult.getLastMessage().getId()).isEqualTo(bobHigherId);
        assertThat(bobResult.getHasUnread()).isFalse();

        ConversationDetailProjection emptyResult = byConversationId.get(emptyConversationId);
        assertThat(emptyResult.getLastMessage()).isNull();
        assertThat(emptyResult.getHasUnread()).isFalse();
    }

    @ParameterizedTest(name = "정렬 방향={0}")
    @EnumSource(SortDirection.class)
    @DisplayName("생성 시각과 ID로 양방향 커서 조회하고 커서와 관계없이 전체 개수를 유지한다.")
    void searchAndSearchCount_PaginateByCreatedAtAndId(SortDirection direction){
        User requester = persistUser("requester@example.com", "요청자");
        List<User> others = new ArrayList<>();
        IntStream.range(0,5).forEach(i ->
            others.add(persistUser("other" + i + "@example.com", "대화 상대 " + i)));

        entityManager.flush();
        UUID requesterId = requester.getId();

        List<UUID> conversationIds = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        LocalDateTime base = LocalDateTime.of(2026, 11, 12, 10, 0, 0, 123456000);

        List<LocalDateTime> createdAts = List.of(
                base.minusNanos(1_000),
                base,
                base,
                base,
                base.plusNanos(1_000));

        IntStream.range(0, conversationIds.size())
                .forEach(i -> {
                            insertConversation(conversationIds.get(i),
                                    requester,
                                    others.get(i),
                                    createdAts.get(i));

                            insertMessage(
                                    UUID.randomUUID(),
                                    conversationIds.get(i),
                                    requesterId,
                                    others.get(i).getId(),
                                    "메시지 " + i,
                                    true,
                                    base.plusMinutes(5-i)
                            );
                        });

        entityManager.clear();

        List<UUID> expectedOrder = new ArrayList<>(conversationIds);
        if (direction == SortDirection.DESCENDING){
            Collections.reverse(expectedOrder);
        }

        int limit =2;
        String cursor = null;
        UUID idAfter = null;

        for (int offset =0; offset < expectedOrder.size(); offset += limit){
            ConversationSearchCondition condition = new ConversationSearchCondition(
                    null, cursor, idAfter, limit, direction,
                    ConversationSortBy.CREATED_AT, requesterId);

            List<ConversationDetailProjection> page = conversationRepository.search(condition);
            int expectedEnd = Math.min(offset + limit + 1, expectedOrder.size());

            assertThat(page)
                    .extracting(projection -> projection.getConversation().getId())
                    .containsExactlyElementsOf(expectedOrder.subList(offset, expectedEnd));

            assertThat(conversationRepository.searchCount(condition))
                    .isEqualTo(5L);
            int lastReturnedIndex = Math.min(limit, page.size()) - 1;
            Conversation boundary = page.get(lastReturnedIndex).getConversation();

            cursor = boundary.getCreatedAt().toString();
            idAfter = boundary.getId();
        }
    }

    @ParameterizedTest(name = "w정렬 방향={0}")
    @EnumSource(SortDirection.class)
    @DisplayName("커서 기준 대화방이 없어도 전달받은 생성 시각과 ID로 다음 대화방을 조회한다.")
    void search_usesCursorValuesWhenBoundaryConversationDoesNotExist(SortDirection direction){
        User requester = persistUser("requester@example.com", "요청자");
        User firstOther = persistUser("first@example.com", "첫 번째 상대");
        User secondOther = persistUser("second@example.com", "두 번째 상대");

        entityManager.flush();

        UUID requesterId = requester.getId();
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID missingCursorId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 12, 10, 0, 0, 123456000);
        insertConversation(lowerId, requester, firstOther, createdAt);
        insertConversation(higherId, requester, secondOther, createdAt);

        entityManager.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(
                null,
                createdAt.toString(),
                missingCursorId,
                10,
                direction,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        UUID expectedId = direction == SortDirection.ASCENDING ? higherId : lowerId;

        assertThat(result)
                .extracting(projection -> projection.getConversation().getId())
                .containsExactly(expectedId);


    }

    @Test
    @DisplayName("조회 대화방 수가 늘어도 SQL 수가 증가하지 않고 프로필 접근 시 추가 SQL이 발생하지 않는다.")
    void search_keepsStatementCountConstantAsResultSizeGrows(){
        User requester = persistUserWithProfile(
                "requester@example.com",
                "요청자",
                "profiles/requester.png"
        );

        List<User> others = new ArrayList<>();

        IntStream.range(0, 10).forEach(i ->
            others.add(persistUserWithProfile(
                    "other" + i + "@example.com",
                    "대화 상대 " + i,
                    "profiles/other-" + i + ".png"
            )));

        entityManager.flush();

        UUID requesterId = requester.getId();
        LocalDateTime base = LocalDateTime.of(2026, 11, 12, 10, 0);

        IntStream.range(0, others.size()).forEach(i ->{
            UUID conversationId = UUID.randomUUID();
            User other = others.get(i);

            insertConversation(
                    conversationId,
                    requester,
                    other,
                    base.plusSeconds(i)
            );

            insertMessage(
                    UUID.randomUUID(),
                    conversationId,
                    other.getId(),
                    requesterId,
                    "프로필 조회 확인 메시지 " + i,
                    true,
                    base.plusSeconds(i + 1)
            );
        });

        long smallStatementCount = measureSearchStatementCount(requesterId, 1, 2);
        long largeStatementCount = measureSearchStatementCount(requesterId, 9, 10);

        assertThat(largeStatementCount)
                .as("조회 결과가 2개에서 10개로 늘어도 SQL 수는 동일해야 한다.")
                .isEqualTo(smallStatementCount);

    }



    private long measureSearchStatementCount(
            UUID requesterId,
            int limit,
            int expectedSize
    ){
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();

        assertThat(statistics.isStatisticsEnabled())
                .isTrue();
        entityManager.clear();
        statistics.clear();

        ConversationSearchCondition condition = new ConversationSearchCondition(
                null,
                null,
                null,
                limit,
                SortDirection.DESCENDING,
                ConversationSortBy.CREATED_AT,
                requesterId
        );

        List<ConversationDetailProjection> result = conversationRepository.search(condition);
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(result).hasSize(expectedSize);
        assertThat(statementCount).isPositive();

        result.forEach(projection ->{
            Conversation conversation = projection.getConversation();
            DirectMessage message = projection.getLastMessage();

            assertThat(message).isNotNull();

            List.of(conversation.getUserA(),
                    conversation.getUserB(),
                    message.getSender(),
                    message.getReceiver())
                    .forEach(user ->{
                        assertThat(user.getProfileImage()).isNotNull();
                        assertThat(user.getProfileImage().getKey()).isNotBlank();
                    });
        });
        assertThat(statistics.getPrepareStatementCount())
                .as("프로필 접근 후에도 SQL 수가 증가하지 않아야 한다.")
                .isEqualTo(statementCount);
        return statementCount;
    }

    private User persistUserWithProfile(
            String email,
            String name,
            String fileKey
    ) {
        User user = persistUser(email, name);

        FileInfo profile = FileInfo.createS3File(
                fileKey,
                "profile.png",
                "image/png",
                100L
        );

        entityManager.persist(profile);
        user.updateProfileImage(profile);

        return user;
    }

    private void insertConversation(
            UUID conversationId,
            User firstUser,
            User secondUser,
            LocalDateTime createdAt
    ) {
        ConversationCommand pair = new ConversationCommand(firstUser, secondUser);

        jdbcTemplate.update("""
              INSERT INTO conversations ( id, user_a_id, user_b_id, created_at )
              VALUES (?, ?, ?, ?)
              """, conversationId, pair.userA().getId(), pair.userB().getId(), createdAt);
    }

    private void insertMessage(UUID messageId, UUID conversationId, UUID senderId, UUID receiverId, String content, boolean read, LocalDateTime createdAt){
        jdbcTemplate.update("""
                INSERT INTO direct_messages (
                        id, conversation_id, sender_id, receiver_id, content, is_read, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, messageId, conversationId, senderId, receiverId, content, read, createdAt);
    }

    private static Stream<Arguments> counterpartNameSearchCases() {
        return Stream.of(
                Arguments.of("aLiCe", List.of("Alice")),
                Arguments.of("LiC", List.of("Alice")),
                Arguments.of("  aLiCe  ", List.of("Alice")),
                Arguments.of("bOb", List.of("Bob")),
                Arguments.of("", List.of("Alice", "Bob")),
                Arguments.of("   ", List.of("Alice", "Bob")),
                Arguments.of("Requester", List.of()),
                Arguments.of("not-found", List.of())
        );
    }

    private User persistUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .name(name)
                .role(Role.USER)
                .build();

        entityManager.persist(user);
        return user;
    }



}
