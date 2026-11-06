package com.codeit.sb13.playlist_for_everyone.domain.user.repository;

import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL에 Flyway를 적용하여 User–FileInfo 매핑과 제약조건을 검증한다.
 * S3 업로드·삭제 및 HTTP API는 이 테스트의 범위가 아니다.
 */
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.docker.compose.enabled=false"
        }
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL 사용자 프로필 이미지 연동")
class UserProfileImageIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        // test 프로필의 H2 대신 이 클래스 전용 PostgreSQL에 연결한다.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );
    }


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileInfoRepository fileInfoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("프로필 이미지가 없는 여러 사용자를 저장할 수 있다")
    void 이미지_없는_여러_사용자를_저장한다() {
        User first = userRepository.saveAndFlush(
                createUser("first@example.com")
        );
        User second = userRepository.saveAndFlush(
                createUser("second@example.com")
        );

        UUID firstId = first.getId();
        UUID secondId = second.getId();

        // 영속성 컨텍스트의 객체가 아닌 DB 저장 결과를 다시 읽는다.
        entityManager.clear();

        User foundFirst = userRepository.findById(firstId).orElseThrow();
        User foundSecond = userRepository.findById(secondId).orElseThrow();

        // UNIQUE 제약이 있어도 이미지 없는 사용자는 여러 명 존재할 수 있다.
        assertThat(foundFirst.getProfileImage()).isNull();
        assertThat(foundSecond.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("프로필 이미지 연결을 저장하고 재조회할 수 있다")
    void 프로필_이미지_연결을_저장하고_조회한다() {
        // cascade 저장을 사용하지 않으므로 파일 정보를 먼저 저장한다.
        FileInfo image = saveImage("profiles/first.png");

        User user = createUser("profile@example.com");
        user.updateProfileImage(image);
        User savedUser = userRepository.saveAndFlush(user);

        UUID userId = savedUser.getId();
        UUID imageId = image.getId();

        entityManager.clear();

        User found = userRepository.findById(userId).orElseThrow();

        assertThat(found.getProfileImage()).isNotNull();
        assertThat(found.getProfileImage().getId()).isEqualTo(imageId);
        assertThat(found.getProfileImage().getKey())
                .isEqualTo("profiles/first.png");
    }

    @Test
    @DisplayName("이미지를 교체하면 연결만 바뀌고 기존 파일 정보는 유지된다")
    void 이미지_교체_시_기존_파일_정보를_유지한다() {
        FileInfo oldImage = saveImage("profiles/old.png");
        FileInfo newImage = saveImage("profiles/new.png");

        User user = createUser("replace@example.com");
        user.updateProfileImage(oldImage);
        User savedUser = userRepository.saveAndFlush(user);

        UUID userId = savedUser.getId();
        UUID oldImageId = oldImage.getId();
        UUID newImageId = newImage.getId();

        // 관리 상태의 엔티티 변경이 DB에 반영되는지 확인한다.
        savedUser.updateProfileImage(newImage);
        entityManager.flush();
        entityManager.clear();

        User found = userRepository.findById(userId).orElseThrow();

        assertThat(found.getProfileImage().getId()).isEqualTo(newImageId);

        // 연결 교체만으로 기존 FileInfo가 자동 삭제되어서는 안 된다.
        assertThat(fileInfoRepository.existsById(oldImageId)).isTrue();
    }

    @Test
    @DisplayName("프로필 연결을 해제해도 파일 정보는 자동 삭제되지 않는다")
    void 연결_해제_시_파일_정보를_유지한다() {
        FileInfo image = saveImage("profiles/remove.png");

        User user = createUser("remove@example.com");
        user.updateProfileImage(image);
        User savedUser = userRepository.saveAndFlush(user);

        UUID userId = savedUser.getId();
        UUID imageId = image.getId();

        savedUser.removeProfileImage();
        entityManager.flush();
        entityManager.clear();

        User found = userRepository.findById(userId).orElseThrow();

        assertThat(found.getProfileImage()).isNull();
        assertThat(fileInfoRepository.existsById(imageId)).isTrue();
    }

    @Test
    @DisplayName("같은 파일을 두 사용자의 프로필로 연결하면 거절한다")
    void 동일_파일의_중복_연결을_거절한다() {
        FileInfo image = saveImage("profiles/shared.png");

        User first = createUser("owner@example.com");
        first.updateProfileImage(image);
        userRepository.saveAndFlush(first);

        User second = createUser("other@example.com");
        second.updateProfileImage(image);

        // 다른 이메일을 사용하여 프로필 파일의 UNIQUE 제약을 검증한다.
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uq_users_profile_image");

    }

    @Test
    @DisplayName("존재하지 않는 파일 ID로 프로필을 연결하면 거절한다")
    void 존재하지_않는_파일_연결을_거절한다() {
        User user = userRepository.saveAndFlush(
                createUser("missing-file@example.com")
        );

        UUID missingFileId = UUID.randomUUID();

        // JPA 객체 상태와 별개로 DB의 실제 FK 제약을 검증한다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                UPDATE users
                SET profile_image_id = ?
                WHERE id = ?
                """,
                missingFileId,
                user.getId()
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_users_profile_image");
    }

    @Test
    @DisplayName("사용자가 참조 중인 파일 정보를 삭제하면 거절한다")
    void 참조_중인_파일_삭제를_거절한다() {
        FileInfo image = saveImage("profiles/in-use.png");

        User user = createUser("in-use@example.com");
        user.updateProfileImage(image);
        userRepository.saveAndFlush(user);

        // 파일 삭제 전에 사용자 연결을 해제하거나 교체해야 한다.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM file_infos WHERE id = ?",
                image.getId()
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_users_profile_image");
    }

    @Test
    @DisplayName("기존 profiles 테이블을 제거하고 사용자와 파일 테이블은 유지한다")
    void 기존_프로필_테이블을_제거한다() {
        // 실제 PostgreSQL에 Flyway 마이그레이션을 적용한 결과를 확인한다.
        // to_regclass()는 해당 테이블이 없으면 null을 반환한다.
        String profilesTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.profiles')::text",
                String.class
        );

        String usersTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.users')::text",
                String.class
        );

        String filesTable = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.file_infos')::text",
                String.class
        );

        assertThat(profilesTable).isNull();
        assertThat(usersTable).isEqualTo("users");
        assertThat(filesTable).isEqualTo("file_infos");
    }

    private User createUser(String email) {
        return User.builder()
                .email(email)
                .name("프로필 사용자")
                .password("encoded-password")
                .role(Role.USER)
                .build();
    }

    private FileInfo saveImage(String key) {
        FileInfo image = FileInfo.createS3File(
                key,
                "profile.png",
                "image/png",
                1024L
        );

        return fileInfoRepository.saveAndFlush(image);
    }

}
