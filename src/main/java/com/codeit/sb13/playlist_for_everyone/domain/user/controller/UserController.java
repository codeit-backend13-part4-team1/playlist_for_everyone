package com.codeit.sb13.playlist_for_everyone.domain.user.controller;

import com.codeit.sb13.playlist_for_everyone.domain.auth.service.PasswordChangeService;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.*;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserAdministrationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {


    private final UserService userService;
    private final UserAdministrationService userAdministrationService;
    private final PasswordChangeService passwordChangeService;

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request) {

        UserDto response = userService.create(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 관리자 사용자 목록 조회
     * 권한은 SecurityConfig의 URL 규칙(GET /api/users -> ADMIN)으로 확인 한다.
     */
    @GetMapping
    public ResponseEntity<CursorResponse<UserDto>> getUsers(@Valid UserSearchRequest request) {

        CursorResponse<UserDto> response = userAdministrationService.getUsers(request.toCondition());

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 상세 조회
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUser(@PathVariable("userId") UUID userId) {

        UserDto response = userService.getUserDtoById(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 프로필 변경
     */
    @PatchMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> updateProfile(@PathVariable("userId") UUID userId, @Valid @RequestPart("request") UserUpdateRequest request,
                                                 @RequestPart(value = "image", required = false) MultipartFile image) {
        UserDto response = userService.updateProfile(userId, request.name(), image);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> updateRole(@PathVariable("userId") UUID userId,
                                            @Valid @RequestBody UserRoleUpdateRequest request) {

        userAdministrationService.updateRole(userId, request.role());

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/locked")
    public ResponseEntity<Void> updateLocked(@PathVariable("userId") UUID userId,
                                             @Valid @RequestBody UserLockUpdateRequest request) {

        userAdministrationService.updateLocked(userId, request.locked());

        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable("userId") UUID userId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        passwordChangeService.changePassword(userId, request.password());

        return ResponseEntity.noContent().build();
    }

}
