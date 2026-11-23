package com.aigicrm.module.fabushi;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 法布施内容类型单源（D1 2026-11-23 重构）：每日诵读 / 每日随施 / 再读一本。
 * 替代旧 heart(心得)/recitation(诵读)/znl(正能量)——存量数据已由 V2026.11.23.1 迁移
 * （recitation→songdu，heart+znl→suishi）。双端字面量禁止另行定义，统一引用本枚举
 * （PC/MP 常量在各自 api 层与 codes() 保持一致，契约 check-fabushi-scope-types.sql 兜底）。
 */
public enum FabushiScopeType {
    SONGDU("songdu", "每日诵读"),
    SUISHI("suishi", "每日随施"),
    ZAIDU("zaidu", "再读一本");

    private final String code;
    private final String label;

    FabushiScopeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Set<String> codes() {
        return Arrays.stream(values()).map(FabushiScopeType::getCode).collect(Collectors.toUnmodifiableSet());
    }

    public static String labelOf(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
                .map(FabushiScopeType::getLabel).orElse(code);
    }

    /** 是否必选书目（Q4 拍板：songdu/zaidu 必选书如同四项激励先选客户；suishi 免标题免书目）。 */
    public boolean requiresBook() {
        return this != SUISHI;
    }
}
