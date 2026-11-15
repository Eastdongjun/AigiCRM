package com.aigicrm.common.security;

import com.aigicrm.common.BusinessException;

/**
 * 统一密码策略（2026-11-16 安全审计 P0）。
 * 8–30 位，至少包含一个字母和一个数字；忘记密码、修改密码、首登改密共用。
 * 管理员随机临时口令由 generateRandomPassword() 生成，天然满足策略。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 30;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException(400, "密码长度须为 8-30 位");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BusinessException(400, "密码须同时包含字母和数字");
        }
    }
}
