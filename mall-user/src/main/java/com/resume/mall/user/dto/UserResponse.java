package com.resume.mall.user.dto;

import com.resume.mall.user.entity.MallUser;

public record UserResponse(
        Long id,
        String username,
        String role,
        String level,
        Integer status
) {
    public static UserResponse from(MallUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLevel(),
                user.getStatus());
    }
}
