package com.resume.mall.common;

public record JwtClaims(
        String jti,
        long userId,
        String username,
        String role,
        String level,
        long exp
) {
}
