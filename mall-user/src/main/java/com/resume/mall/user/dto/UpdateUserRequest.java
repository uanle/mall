package com.resume.mall.user.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateUserRequest(
        @Pattern(regexp = "USER|ADMIN", message = "角色只能是 USER 或 ADMIN") String role,
        @Pattern(regexp = "NONE|NORMAL|VIP|SVIP", message = "用户等级只能是 NONE、NORMAL、VIP 或 SVIP") String level,
        Integer status
) {
}
