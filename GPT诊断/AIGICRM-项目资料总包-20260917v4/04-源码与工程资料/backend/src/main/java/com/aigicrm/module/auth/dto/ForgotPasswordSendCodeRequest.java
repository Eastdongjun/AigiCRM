package com.aigicrm.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgotPasswordSendCodeRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "请选择验证方式")
    @Pattern(regexp = "^(phone|email)$", message = "验证方式无效")
    private String method;
}
