package com.resume.mall.user.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.UserHeaders;
import com.resume.mall.user.dto.ChangePasswordRequest;
import com.resume.mall.user.dto.ChangeUsernameRequest;
import com.resume.mall.user.dto.LoginRequest;
import com.resume.mall.user.dto.LoginResponse;
import com.resume.mall.user.dto.RegisterRequest;
import com.resume.mall.user.dto.UpdateUserRequest;
import com.resume.mall.user.dto.UserResponse;
import com.resume.mall.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "用户服务", description = "注册、登录、当前用户、用户管理接口")
@RestController
@RequestMapping("/api")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户注册", description = "默认注册为普通用户 NORMAL 等级。")
    @PostMapping("/auth/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    @Operation(summary = "用户登录", description = "登录成功后返回 Bearer Token。")
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok()
                .header(UserHeaders.USER_ID, String.valueOf(response.user().id()))
                .header(UserHeaders.USER_ROLE, response.user().role())
                .body(ApiResponse.ok(response));
    }

    @Operation(summary = "退出登录", description = "需要通过 Gateway 携带 Authorization: Bearer token。退出后 Redis 中的 token 会话会被删除。")
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(
            @Parameter(hidden = true) @RequestHeader(UserHeaders.TOKEN_ID) String tokenId,
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId) {
        userService.logout(tokenId, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "查询当前登录用户", description = "需要通过 Gateway 携带 Authorization: Bearer token。")
    @GetMapping("/users/me")
    public ApiResponse<UserResponse> me(
            @Parameter(hidden = true) @RequestHeader(value = UserHeaders.USER_ID, required = false) Long userId,
            @Parameter(description = "登录接口返回的 Bearer Token，例如：Bearer eyJhbGciOiJIUzI1NiJ9...")
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "Swagger 测试可直接填写登录响应里的 accessToken，不需要 Bearer 前缀。")
            @RequestHeader(value = "accessToken", required = false) String accessToken) {
        if (userId != null) {
            return ApiResponse.ok(userService.getById(userId));
        }
        return ApiResponse.ok(userService.getByAuthorizationToken(resolveTokenHeader(authorization, accessToken)));
    }

    @Operation(summary = "根据请求头 Token 查询用户信息", description = "直接读取 Authorization: Bearer token，校验 JWT 和 Redis token 会话后返回当前用户信息。")
    @GetMapping("/auth/me")
    public ApiResponse<UserResponse> authMe(
            @Parameter(description = "登录接口返回的 Bearer Token，例如：Bearer eyJhbGciOiJIUzI1NiJ9...")
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Parameter(description = "Swagger 测试可直接填写登录响应里的 accessToken，不需要 Bearer 前缀。")
            @RequestHeader(value = "accessToken", required = false) String accessToken) {
        return ApiResponse.ok(userService.getByAuthorizationToken(resolveTokenHeader(authorization, accessToken)));
    }

    @Operation(summary = "分页查询用户", description = "管理员接口。支持按用户名、角色、等级、状态查询。")
    @GetMapping("/users")
    public ApiResponse<PageResult<Map<String, Object>>> users(
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "status", required = false) Integer status) {
        return ApiResponse.ok(userService.pageUsers(pageNum, pageSize, username, role, level, status));
    }

    @Operation(summary = "更新用户角色/等级/状态", description = "管理员接口。role 支持 USER、ADMIN；level 支持 NORMAL、VIP、SVIP；status 1 启用，0 禁用。")
    @PutMapping("/users/{userId}")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable("userId") long userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.updateUser(userId, request));
    }

    @Operation(summary = "修改当前用户用户名", description = "修改成功后会清理当前用户登录会话，需要重新登录。")
    @PutMapping("/users/me/username")
    public ApiResponse<UserResponse> changeUsername(
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId,
            @Valid @RequestBody ChangeUsernameRequest request) {
        return ApiResponse.ok(userService.changeUsername(userId, request));
    }

    @Operation(summary = "修改当前用户密码", description = "需要校验旧密码；修改成功后会清理当前用户登录会话，需要重新登录。")
    @PutMapping("/users/me/password")
    public ApiResponse<Void> changePassword(
            @Parameter(hidden = true) @RequestHeader(UserHeaders.USER_ID) long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除用户", description = "软删除：将用户 status 更新为 0，并清理该用户的登录会话。")
    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable("userId") long userId) {
        userService.deleteUser(userId);
        return ApiResponse.ok(null);
    }

    private String resolveTokenHeader(String authorization, String accessToken) {
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }
        if (accessToken != null && !accessToken.isBlank()) {
            String value = accessToken.trim();
            return value.startsWith("Bearer ") ? value : "Bearer " + value;
        }
        return authorization;
    }
}
