package com.aigicrm.module.fabushi;

import java.util.Arrays;
import java.util.Map;
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

    /**
     * 【GL-11 2026-11-24 过渡兼容】旧客户端别名：线上小程序的旧版本仍会发重构前的类型值，
     * 后端先于小程序上线时必须认得。recitation(诵读)→songdu；heart(心得)、znl(正能量)→suishi。
     * 只在入口归一，落库一律是新值（存量数据已由 V2026.11.23.1 迁移，库里不应再有旧值）。
     * 线上小程序全部升级到发新值的版本之后，删除本表与 toCurrent / normalize 的调用点。
     */
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
            "recitation", SONGDU.getCode(),
            "heart", SUISHI.getCode(),
            "znl", SUISHI.getCode());

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

    /**
     * 旧别名换成现行取值；其它输入（现行取值、未知值、空白、null）原样返回。
     * 用于列表筛选：未知值保持原样，查不到任何行——与重构前的行为一致，不能因为"不认识"就变成不过滤。
     * 只做精确匹配，不做大小写或空白容错。
     */
    public static String toCurrent(String raw) {
        // Map.of 做出的映射不接受 null 键: 先判空
        return raw == null ? null : LEGACY_ALIASES.getOrDefault(raw, raw);
    }

    /**
     * 把请求里的类型归一成现行取值：现行取值原样返回，旧别名映射为新值；
     * 白名单之外的取值（含 null、空白、未知值）返回 null，由调用方按非法处理。用于发布入口。
     */
    public static String normalize(String raw) {
        String code = toCurrent(raw);
        return code != null && codes().contains(code) ? code : null;
    }
}
