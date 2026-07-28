package com.ycmn.module.auth.dto;

import lombok.Data;

@Data
public class ForgotPasswordLookupResponse {
    private String maskedPhone;
    private String maskedEmail;
    private boolean hasPhone;
    private boolean hasEmail;
}
