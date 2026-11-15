package com.aigicrm.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;
    /** 机器可读错误码(可选): PERM_DENIED/DATA_SCOPE_DENIED/FIELD_PERM_REQUIRED/ADMIN_ONLY/ORG_ADMIN_REQUIRED */
    private final String errorCode;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
        this.errorCode = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }

    public BusinessException(int code, String errorCode, String message) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }
}
