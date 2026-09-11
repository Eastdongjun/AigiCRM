package com.aigicrm.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordLookupRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
}
