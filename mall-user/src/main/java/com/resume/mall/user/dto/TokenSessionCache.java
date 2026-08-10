package com.resume.mall.user.dto;

public record TokenSessionCache(
        String tokenId,
        Long userId,
        String username,
        String role,
        String level,
        Long issuedAt,
        Long expiresAt
) {
}
