package com.resume.mall.common;

public record JwtClaims(
        long userId,
        String username,
        String role,
        String level,
        long exp
) {
}
