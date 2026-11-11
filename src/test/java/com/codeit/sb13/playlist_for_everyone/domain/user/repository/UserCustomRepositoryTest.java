package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.UserSearchCondition;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.domain.SortDirection;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        JpaAuditingConfig.class, QueryDslConfig.class
})
@DisplayName("사용자 목록 조회 레포지토리")
class UserCustomRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    //-------------- 필터

    @Test
    @DisplayName("필터가 없으면 전체 사용자를 이름 오름차순으로 조회한다.")
    void 필터가_없으면_이름_오름차순으로_조회한다() {
        saveUser("c@test.com", "charlie", Role.USER);
        saveUser("a@test.com", "alice", Role.USER);
        saveUser("b@test.com", "bob", Role.ADMIN);

        List<User> result = userRepository.search(filter(null, null, null));

        assertThat(result).extracting(User::getName)
                .containsExactly("alice", "bob", "charlie");
    }


    @Test
    @DisplayName("emailLike는 대소문자 구분 없이 부분 일치로 조회한다.")
    void emailLike는_대소문자_구분_없이_부분_일치로_조회한다() {
        saveUser("alice@test.com", "a1", Role.USER);
        saveUser("ALICE2@other.com", "a2", Role.USER);
        saveUser("bob@test.com", "b", Role.USER);

        List<User> result = userRepository.search(filter("alice", null, null));

        assertThat(result).extracting(User::getEmail)
                .containsExactly("alice@test.com", "ALICE2@other.com");

    }

    @Test
    @DisplayName("roleEqual로 해당 권한의 사용자만 조회한다.")
    void roleEqual로_해당_권한의_사용자만_조회한다() {
        saveUser("admin@test.com", "admin", Role.ADMIN);
        saveUser("user@test.com", "user", Role.USER);

        List<User> result = userRepository.search(filter(null, Role.ADMIN, null));

        assertThat(result).extracting(User::getEmail).containsExactly("admin@test.com");
    }

    @Test
    @DisplayName("isLocked가 true면 잠긴 사용자만 조회한다.")
    void isLocked가_true면_잠긴_사용자만_조회한다() {
        saveLockedUser("locked@test.com", "locked");
        saveUser("active@test.com", "active", Role.USER);

        List<User> result = userRepository.search(filter(null, null, true));

        assertThat(result).extracting(User::getEmail).containsExactly("locked@test.com");
    }

    @Test
    @DisplayName("isLocked가 false면 잠기지 않은 사용자만 조회한다.")
    void isLocked가_false면_잠기지_않은_사용자만_조회한다() {
        saveLockedUser("locked@test.com", "locked");
        saveUser("active@test.com", "active", Role.USER);

        List<User> result = userRepository.search(filter(null, null, false));

        assertThat(result).extracting(User::getEmail).containsExactly("active@test.com");
    }

    @Test
    @DisplayName("여러 필터를 함께 적용한다.")
    void 여러_필터를_함께_적용한다() {
        saveLockedUser("target@test.com", "target");
        saveLockedUser("other@test.com", "other-locked");
        saveUser("target2@test.com", "active", Role.USER);

        List<User> result = userRepository.search(filter("target", Role.USER, true));

        assertThat(result).extracting(User::getEmail).containsExactly("target@test.com");
    }

    //-------------- 정렬
    @Test
    @DisplayName("이메일 내림차순으로 조회한다.")
    void 이메일_내림차순으로_조회한다() {
        saveUser("a@test.com", "n", Role.USER);
        saveUser("c@test.com", "n", Role.USER);
        saveUser("b@test.com", "n", Role.USER);

        List<User> result = userRepository.search(
                page(UserSortBy.EMAIL, SortDirection.DESCENDING, null, null, 10));

        assertThat(result).extracting(User::getEmail)
                .containsExactly("c@test.com", "b@test.com", "a@test.com");
    }

    @Test
    @DisplayName("가입일 내림차순으로 조회한다.")
    void 가입일_내림차순으로_조회한다() {
        setCreatedAt(saveUser("old@test.com", "n", Role.USER), LocalDateTime.of(2026, 10, 1, 0, 0));
        setCreatedAt(saveUser("new@test.com", "n", Role.USER), LocalDateTime.of(2026, 10, 3, 0, 0));
        setCreatedAt(saveUser("mid@test.com", "n", Role.USER), LocalDateTime.of(2026, 10, 2, 0, 0));

        List<User> result = userRepository.search(
                page(UserSortBy.CREATED_AT, SortDirection.DESCENDING, null, null, 10));

        assertThat(result).extracting(User::getEmail)
                .containsExactly("new@test.com", "mid@test.com", "old@test.com");
    }

    @Test
    @DisplayName("잠금 상태 오름차순이면 잠기지 않은 사용자가 먼저 조회된다.")
    void 잠금_상태_오름차순이면_잠기지_않은_사용자가_먼저_조회된다() {
        saveLockedUser("locked@test.com", "n");
        saveUser("active@test.com", "n", Role.USER);

        List<User> result = userRepository.search(
                page(UserSortBy.IS_LOCKED, SortDirection.ASCENDING, null, null, 10));

        assertThat(result).extracting(User::getEmail)
                .containsExactly("active@test.com", "locked@test.com");
    }

    @Test
    @DisplayName("권한 오름차순이면 ADMIN이 먼저 조회된다.")
    void 권한_오름차순이면_ADMIN이_먼저_조회된다() {
        saveUser("user@test.com", "n", Role.USER);
        saveUser("admin@test.com", "n", Role.ADMIN);

        List<User> result = userRepository.search(
                page(UserSortBy.ROLE, SortDirection.ASCENDING, null, null, 10));

        assertThat(result).extracting(User::getEmail)
                .containsExactly("admin@test.com", "user@test.com");
    }

    // -------------- 페이지

    @Test
    @DisplayName("다음 페이지 확인을 위해 limit보다 하나 더 조회한다.")
    void 다음_페이지_확인을_위해_limit보다_하나_더_조회한다() {
        saveUser("a@test.com", "a", Role.USER);
        saveUser("b@test.com", "b", Role.USER);
        saveUser("c@test.com", "c", Role.USER);

        List<User> result = userRepository.search(
                page(UserSortBy.NAME, SortDirection.ASCENDING, null, null, 2));

        assertThat(result).hasSize(3);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "NAME, ASCENDING", "NAME, DESCENDING",
            "EMAIL, ASCENDING", "EMAIL, DESCENDING",
            "CREATED_AT, ASCENDING", "CREATED_AT, DESCENDING",
            "IS_LOCKED, ASCENDING", "IS_LOCKED, DESCENDING",
            "ROLE, ASCENDING", "ROLE, DESCENDING"
    })
    @DisplayName("한 건씩 커서로 이어 조회하면 한 번에 조회한 순서와 같고 중복·누락이 없다.")
    void 커서로_이어_조회하면_한_번에_조회한_순서와_같다(UserSortBy sortBy, SortDirection direction) {
        // 정렬 값이 같은 사용자를 일부러 섞어, id 보조 정렬까지 검증한다.
        User a = saveUser("a@test.com", "same", Role.USER);
        User b = saveUser("b@test.com", "same", Role.ADMIN);
        User c = saveLockedUser("c@test.com", "zed");
        User d = saveUser("d@test.com", "abe", Role.USER);
        User e = saveUser("e@test.com", null, Role.USER);
        setCreatedAt(a, LocalDateTime.of(2026, 10, 2, 0, 0));
        setCreatedAt(b, LocalDateTime.of(2026, 10, 2, 0, 0));
        setCreatedAt(c, LocalDateTime.of(2026, 10, 3, 0, 0));
        setCreatedAt(d, LocalDateTime.of(2026, 10, 1, 0, 0));

        List<UUID> expected = userRepository.search(page(sortBy, direction, null, null, 10))
                .stream().map(User::getId).toList();

        List<UUID> walked = new ArrayList<>();
        String cursor = null;
        UUID idAfter = null;
        for (int i = 0; i < 10; i++) {   // 무한 반복 방지
            List<User> result = userRepository.search(page(sortBy, direction, cursor, idAfter, 1));
            if (result.isEmpty()) {
                break;
            }
            User last = result.get(0);
            walked.add(last.getId());
            cursor = cursorOf(last, sortBy);
            idAfter = last.getId();
        }

        assertThat(expected).hasSize(5);
        assertThat(walked).containsExactlyElementsOf(expected);
    }

    // -------------- count

    @Test
    @DisplayName("전체 개수는 필터만 반영하고 커서는 반영하지 않는다.")
    void 전체_개수는_필터만_반영하고_커서는_반영하지_않는다() {
        User alice = saveUser("a@test.com", "alice", Role.USER);
        saveUser("b@test.com", "bob", Role.USER);
        saveUser("admin@test.com", "admin", Role.ADMIN);

        UserSearchCondition condition = new UserSearchCondition(
                null, Role.USER, null,
                "alice", alice.getId(),
                10, SortDirection.ASCENDING, UserSortBy.NAME);

        assertThat(userRepository.count(condition)).isEqualTo(2L);
    }

    // -------------- 연관 조회

    @Test
    @DisplayName("프로필 이미지를 함께 조회한다.")
    void 프로필_이미지를_함께_조회한다() {
        saveUserWithProfileImage("with@test.com");
        saveUser("without@test.com", "n", Role.USER);
        em.flush();
        em.clear();

        List<User> result = userRepository.search(filter(null, null, null));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(user -> user.getProfileImage() != null)
                .singleElement()
                .satisfies(user ->
                        assertThat(Hibernate.isInitialized(user.getProfileImage())).isTrue());
    }


    private UserSearchCondition filter(String emailLike, Role role, Boolean isLocked) {
        return new UserSearchCondition(
                emailLike, role, isLocked,
                null, null,
                10, SortDirection.ASCENDING, UserSortBy.NAME
        );
    }

    private UserSearchCondition page(UserSortBy sortBy, SortDirection direction,
                                     String cursor, UUID idAfter, int limit) {
        return new UserSearchCondition(
                null, null, null,
                cursor, idAfter,
                limit, direction, sortBy
        );
    }

    private String cursorOf(User user, UserSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> user.getName() == null ? "" : user.getName();
            case EMAIL -> user.getEmail();
            case CREATED_AT -> user.getCreatedAt().toString();
            case IS_LOCKED -> String.valueOf(user.isLocked());
            case ROLE -> user.getRole().name();
        };
    }

    private User saveUser(String email, String name, Role role) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .name(name)
                .role(role)
                .build()
        );
    }

    private User saveLockedUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .name(name)
                .role(Role.USER)
                .build();
        user.lock();
        return userRepository.saveAndFlush(user);
    }

    private User saveUserWithProfileImage(String email) {
        FileInfo fileInfo = FileInfo.createS3File(
                "profile/" + email, "profile.png", "image/png", 1024L
        );
        em.persist(fileInfo);

        User user = User.builder()
                .email(email)
                .name("n")
                .role(Role.USER)
                .build();
        user.updateProfileImage(fileInfo);
        return userRepository.saveAndFlush(user);
    }

    private void setCreatedAt(User user, LocalDateTime createdAt) {
        em.createNativeQuery("update users set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", user.getId())
                .executeUpdate();
        em.clear();

    }

}