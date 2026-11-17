package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 【2026-11-17 客户名称唯一】客户名称归一化：
 * NFKC（全半角/兼容字符）→ 中文括号统一 → 首尾空格清理 → 内部连续空白折叠 → 拉丁小写。
 * 只用于唯一性判定（name_norm），展示名保留用户输入（仅 trim）。
 * 最大 100 个字符（按 Unicode code point 计，中文/emoji 均按 1 个字符）。
 */
public final class CustomerNameNormalizer {

    public static final int MAX_LENGTH = 100;

    private CustomerNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        // 零宽字符/软连字符：不可见但会造成“看起来同名”却判不同
        s = s.replace("​", "").replace("‌", "").replace("‍", "")
                .replace("﻿", "").replace("­", "");
        // 常见中文括号/书名号/方头括号统一为 ASCII 括号（NFKC 已处理全角圆括号，这里补
        // 兼容中文输入法常见的【】〔〕〖〗）
        s = s.replace('【', '(').replace('】', ')')
                .replace('〔', '(').replace('〕', ')')
                .replace('〖', '(').replace('〗', ')')
                .replace('（', '(').replace('）', ')');
        s = s.trim();
        // 内部连续空白（含全角空格）折叠为一个半角空格
        s = s.replaceAll("[\\s\\u00A0\\u3000]+", " ");
        return s.toLowerCase(Locale.ROOT);
    }

    /** 归一化并校验非空/长度；用于创建/编辑入口。 */
    public static String normalizeOrThrow(String raw) {
        String n = normalize(raw);
        if (n == null || n.isBlank()) {
            throw new BusinessException(400, "客户名称不能为空");
        }
        int cp = n.codePointCount(0, n.length());
        if (cp > MAX_LENGTH) {
            throw new BusinessException(400, "客户名称不能超过 " + MAX_LENGTH + " 个字符");
        }
        return n;
    }

    /** 展示名：只做首尾 trim，不改变用户输入的大小写/全半角/括号。 */
    public static String displayName(String raw) {
        return raw == null ? null : raw.trim();
    }
}
