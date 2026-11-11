package com.codeit.sb13.playlist_for_everyone.domain.user.controller;

import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.UserCreateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.request.UserSearchRequest;
import com.codeit.sb13.playlist_for_everyone.domain.user.dto.response.UserDto;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserAdministrationService;
import com.codeit.sb13.playlist_for_everyone.domain.user.service.UserService;
import com.codeit.sb13.playlist_for_everyone.global.dto.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {


    private final UserService userService;
    private final UserAdministrationService userAdministrationService;

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



}
