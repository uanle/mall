package com.resume.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUsernameRequest(
        @NotBlank(message = "username must not be blank")
        @Size(min = 3, max = 32, message = "username length must be between 3 and 32")
        String username
) {
}
