package com.codeit.sb13.playlist_for_everyone.domain.user.service;


import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.Role;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.User;
import com.codeit.sb13.playlist_for_everyone.domain.user.repository.UserRepository;
import com.codeit.sb13.playlist_for_everyone.global.config.JpaAuditingConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.PasswordEncoderConfig;
import com.codeit.sb13.playlist_for_everyone.global.config.QueryDslConfig;
import com.codeit.sb13.playlist_for_everyone.global.file.entity.FileInfo;
import com.codeit.sb13.playlist_for_everyone.global.file.repository.FileInfoRepository;
import com.codeit.sb13.playlist_for_everyone.global.file.service.FileService;
import com.codeit.sb13.playlist_for_everyone.global.file.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 호출자에게 트랜잭션이 없어도 사용자 응답을 완성할 수 있는지 검증한다.
 *
 * UserService, FileService, Repository는 실제 Spring Bean을 사용한다.
 * 외부 저장소의 URL 생성만 Mock으로 대체한다.
 */
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.docker.compose.enabled=false"
        }
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        JpaAuditingConfig.class,
        QueryDslConfig.class,
        PasswordEncoderConfig.class,
        UserService.class,
        FileService.class
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
// DataJpaTest의 기본 테스트 트랜잭션을 비활성화한다.
// 테스트 트랜잭션이 서비스의 트랜잭션 누락을 가리지 않도록 한다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PostgreSQL 사용자 프로필 이미지 응답 통합 테스트")
class UserProfileImageResponseIntegrationTest {

    private static final String EMAIL = "profile-response@example.com";
    private static final String NAME = "프로필 사용자";
    private static final String FILE_KEY = "profiles/response.png";

    // 실제로 접속하지 않는 테스트용 URL이다.
    private static final String PROFILE_IMAGE_URL =
            "https://example.com/profiles/response.png?signature=test";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                postgres::getDriverClassName
        );
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileInfoRepository fileInfoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private FileStorage fileStorage;


    @AfterEach
    void cleanUp() {
        // 테스트 트랜잭션을 사용하지 않으므로 자동 롤백되지 않는다.
        // 이 클래스 전용 컨테이너에서 생성한 데이터를 직접 정리한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // users가 file_infos를 참조하므로 사용자를 먼저 삭제한다.
            userRepository.deleteAllInBatch();
            fileInfoRepository.deleteAllInBatch();
        });
    }

    @Test
    @DisplayName("이미지 있는 사용자의 DTO를 트랜잭션 없는 호출자에게 반환한다")
    void 이미지_있는_사용자의_DTO를_트랜잭션_밖에서_사용한다() {
        UUID userId = saveUser(true);

        given(fileStorage.getFileUrl(FILE_KEY))
                .willReturn(PROFILE_IMAGE_URL);

        // 준비용 트랜잭션은 이미 종료됐으며, 호출자는 트랜잭션이 없다.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();

        // 직접 new로 생성하지 않고 Spring 프록시를 통해 호출한다.
        // 서비스가 자체 트랜잭션에서 사용자와 파일 정보를 읽고 DTO를 완성한다.
        UserDto result = userService.getUserDtoById(userId);

        // 서비스 호출이 끝난 뒤에도 호출자에게 트랜잭션이 남지 않는다.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();

        // 엔티티나 지연 로딩 프록시가 아닌 완성된 응답 값을 사용할 수 있어야 한다.
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.name()).isEqualTo(NAME);
        assertThat(result.role()).isEqualTo(Role.USER);
        assertThat(result.locked()).isFalse();
        assertThat(result.profileImageUrl()).isEqualTo(PROFILE_IMAGE_URL);

        // 실제 FileService가 DB에서 읽은 저장 키를 URL 생성에 전달했는지 확인한다.
        verify(fileStorage).getFileUrl(FILE_KEY);
    }

    @Test
    @DisplayName("이미지 없는 사용자는 트랜잭션 없는 호출자에게 null URL을 반환한다")
    void 이미지_없는_사용자는_URL_없이_반환한다() {
        UUID userId = saveUser(false);

        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();

        UserDto result = userService.getUserDtoById(userId);

        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .isFalse();

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.profileImageUrl()).isNull();

        // 이미지가 없으면 외부 저장소의 URL 생성도 요청하지 않는다.
        verifyNoInteractions(fileStorage);
    }

    /**
     * 별도 트랜잭션에서 데이터를 저장하고 커밋한다.
     *
     * 엔티티 대신 ID만 반환하여 조회 테스트가 준비 단계의
     * 영속성 컨텍스트에 의존하지 않도록 한다.
     */
    private UUID saveUser(boolean withProfileImage) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            User user = User.builder()
                    .email(EMAIL)
                    .name(NAME)
                    // 이 테스트에서는 로그인이나 비밀번호 검증을 수행하지 않는다.
                    .password("encoded-test-password")
                    .role(Role.USER)
                    .build();

            if (withProfileImage) {
                FileInfo image = FileInfo.createS3File(
                        FILE_KEY,
                        "response.png",
                        "image/png",
                        1024L
                );

                // cascade 저장을 사용하지 않으므로 파일 정보를 먼저 저장한다.
                FileInfo savedImage = fileInfoRepository.saveAndFlush(image);
                user.updateProfileImage(savedImage);
            }

            return userRepository.saveAndFlush(user).getId();
        });
    }

}
