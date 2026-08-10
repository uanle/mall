package com.resume.mall.user.dto;

import com.resume.mall.user.entity.MallUser;

public record UserCache(
        Long id,
        String username,
        String passwordHash,
        String role,
        String level,
        Integer status
) {
    public static UserCache from(MallUser user) {
        return new UserCache(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getRole(),
                user.getLevel(),
                user.getStatus());
    }

    public MallUser toEntity() {
        MallUser user = new MallUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setLevel(level);
        user.setStatus(status);
        return user;
    }
}
