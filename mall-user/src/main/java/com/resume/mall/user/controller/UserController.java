package com.resume.mall.user.controller;

import com.resume.mall.common.ApiResponse;
import com.resume.mall.common.PageResult;
import com.resume.mall.common.UserHeaders;
import com.resume.mall.user.dto.LoginRequest;
import com.resume.mall.user.dto.LoginResponse;
import com.resume.mall.user.dto.RegisterRequest;
import com.resume.mall.user.dto.UpdateUserRequest;
import com.resume.mall.user.dto.UserResponse;
import com.resume.mall.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    @Operation(summary = "查询当前登录用户", description = "需要通过 Gateway 携带 Authorization: Bearer token。")
    @GetMapping("/users/me")
    public ApiResponse<UserResponse> me(@RequestHeader(UserHeaders.USER_ID) long userId) {
        return ApiResponse.ok(userService.getById(userId));
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
}
