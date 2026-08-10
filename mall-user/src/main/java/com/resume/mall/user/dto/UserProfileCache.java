package com.resume.mall.user.dto;

import com.resume.mall.user.entity.MallUser;

public record UserProfileCache(
        Long id,
        String username,
        String role,
        String level,
        Integer status
) {
    public static UserProfileCache from(MallUser user) {
        return new UserProfileCache(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getLevel(),
                user.getStatus());
    }

    public MallUser toEntity() {
        MallUser user = new MallUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setLevel(level);
        user.setStatus(status);
        return user;
    }
}
