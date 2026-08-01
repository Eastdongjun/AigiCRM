package com.ycmn.common.engine;

import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.security.SecurityUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 权限规则引擎 — 将数据库配置的规则转换为运行时的过滤条件
 *
 * 支持五种规则类型：
 *   column_filter   → SQL WHERE 子句
 *   org_filter      → 组织ID过滤
 *   field_visible   → 响应字段可见性
 *   action_control  → 操作权限
 *   time_range      → 时间范围过滤
 */
public class PermissionRuleEngine {

    /**
     * 对一批规则求值，生成合并后的过滤结果
     *
     * @param rules 用户所有生效的权限规则
     * @param user  当前登录用户（提供变量值）
     */
    public static RuleResult evaluate(List<SysPermissionRule> rules, SecurityUser user) {
        if (rules == null || rules.isEmpty()) {
            return RuleResult.EMPTY;
        }

        VariableContext ctx = VariableContext.from(user);
        List<String> whereClauses = new ArrayList<>();
        Set<String> hiddenFields = new LinkedHashSet<>();
        Set<String> visibleFields = new LinkedHashSet<>();
        // 操作控制按 target_entity 分组：{entity: {action: bool}}，避免跨实体一刀切误伤
        Map<String, Map<String, Boolean>> actionsByEntity = new LinkedHashMap<>();

        for (SysPermissionRule rule : rules) {
            if (rule.getStatus() == null || rule.getStatus() != 1) continue;

            switch (rule.getRuleType()) {
                case "column_filter" -> evaluateColumnFilter(rule, ctx, whereClauses);
                case "org_filter"    -> evaluateOrgFilter(rule, ctx, whereClauses);
                case "time_range"    -> evaluateTimeRange(rule, ctx, whereClauses);
                case "field_visible" -> evaluateFieldVisible(rule, hiddenFields, visibleFields);
                case "action_control" -> evaluateActionControl(rule, actionsByEntity);
            }
        }

        return RuleResult.builder()
                .whereClauses(Collections.unmodifiableList(whereClauses))
                .hiddenFields(Collections.unmodifiableSet(hiddenFields))
                .visibleFields(Collections.unmodifiableSet(visibleFields))
                .actionsByEntity(Collections.unmodifiableMap(actionsByEntity))
                .build();
    }

    /**
     * 按实体过滤求值，只返回 WHERE 子句（用于 SQL 注入点避免跨实体污染）
     *
     * @param rules       用户所有生效的权限规则
     * @param user        当前登录用户
     * @param targetEntity 目标实体（如 "customer"），只取匹配的规则
     * @return 合并后的 WHERE 子句字符串，无匹配时返回空串
     */
    public static String evaluateForEntity(List<SysPermissionRule> rules, SecurityUser user, String targetEntity) {
        if (rules == null || rules.isEmpty() || targetEntity == null) return "";

        VariableContext ctx = VariableContext.from(user);
        List<String> whereClauses = new ArrayList<>();

        for (SysPermissionRule rule : rules) {
            if (rule.getStatus() == null || rule.getStatus() != 1) continue;
            if (!targetEntity.equals(rule.getTargetEntity())) continue; // 跨实体过滤

            switch (rule.getRuleType()) {
                case "column_filter" -> evaluateColumnFilter(rule, ctx, whereClauses);
                case "org_filter"    -> evaluateOrgFilter(rule, ctx, whereClauses);
                case "time_range"    -> evaluateTimeRange(rule, ctx, whereClauses);
                // field_visible 和 action_control 不生成 WHERE，跳过
            }
        }

        return whereClauses.isEmpty() ? "" : String.join(" AND ", whereClauses);
    }

    // ======================== 各类型求值 ========================

    /** 字段值过滤：WHERE dept_id = 5 或 WHERE owner_id IN (1,2,3) */
    private static void evaluateColumnFilter(SysPermissionRule rule, VariableContext ctx,
                                              List<String> clauses) {
        String value = resolve(rule.getRuleValue(), ctx);
        if (value == null || value.isEmpty()) return;

        String op = rule.getOperator();
        String col = escapeColumn(rule.getTargetColumn());

        switch (op) {
            case "eq"  -> clauses.add(col + " = " + quoteValue(value));
            case "ne"  -> clauses.add(col + " != " + quoteValue(value));
            case "in"  -> clauses.add(col + " IN (" + value + ")");
            case "like" -> clauses.add(col + " LIKE " + quoteValue("%" + value + "%"));
            case "is_null" -> clauses.add(col + " IS NULL");
        }
    }

    /** 组织过滤：WHERE org_id IN (递归子节点) 或 WHERE org_id = ? */
    private static void evaluateOrgFilter(SysPermissionRule rule, VariableContext ctx,
                                           List<String> clauses) {
        String value = resolve(rule.getRuleValue(), ctx);
        if (value == null || value.isEmpty()) return;

        String op = rule.getOperator();
        String col = escapeColumn(rule.getTargetColumn());

        if ("in".equals(op)) {
            // 此时 value 已是 "1,2,3" 格式的 ID 列表
            clauses.add(col + " IN (" + value + ")");
        } else if ("eq".equals(op)) {
            clauses.add(col + " = " + quoteValue(value));
        }
    }

    /** 时间范围：WHERE create_time BETWEEN '2024-01-01' AND '2024-01-31' */
    private static void evaluateTimeRange(SysPermissionRule rule, VariableContext ctx,
                                           List<String> clauses) {
        String value = resolve(rule.getRuleValue(), ctx);
        if (value == null || value.isEmpty()) return;

        String col = escapeColumn(rule.getTargetColumn());

        if ("between".equals(rule.getOperator())) {
            String[] parts = value.split(",");
            if (parts.length == 2) {
                clauses.add(col + " BETWEEN " + quoteValue(parts[0].trim())
                        + " AND " + quoteValue(parts[1].trim()));
            }
        }
    }

    /** 字段可见性：读取 rule_value JSON 中的 hidden/visible 列表 */
    private static void evaluateFieldVisible(SysPermissionRule rule,
                                              Set<String> hidden, Set<String> visible) {
        String raw = rule.getRuleValue();
        if (raw == null) return;
        try {
            parseFieldPermissions(raw, hidden, visible);
        } catch (Exception ignored) {}
    }

    /** 操作控制：读取 rule_value JSON 中的操作权限，按 target_entity 分组。
     *  target_entity 为空/未声明的规则不生效（守卫必须显式绑定实体，防止全局误伤） */
    private static void evaluateActionControl(SysPermissionRule rule,
                                               Map<String, Map<String, Boolean>> actionsByEntity) {
        if (rule.getTargetEntity() == null || rule.getTargetEntity().isBlank()) return;
        String raw = rule.getRuleValue();
        if (raw == null) return;
        try {
            Map<String, Boolean> actions = actionsByEntity
                    .computeIfAbsent(rule.getTargetEntity().trim(), k -> new LinkedHashMap<>());
            parseActionPermissions(raw, actions);
        } catch (Exception ignored) {}
    }

    // ======================== 变量替换 ========================

    /** 替换规则值中的 ${变量} */
    static String resolve(String template, VariableContext ctx) {
        if (template == null) return null;
        String result = template;
        for (Map.Entry<String, String> entry : ctx.variables.entrySet()) {
            String value = entry.getValue();
            if (value == null) continue; // 防御：变量值为 null 时跳过，避免 NPE
            result = result.replace("${" + entry.getKey() + "}", value);
        }
        return result;
    }

    // ======================== 安全工具 ========================

    /** 列名校验，防SQL注入 */
    static String escapeColumn(String col) {
        if (col == null) return null;
        if (!col.matches("^[a-zA-Z_][a-zA-Z0-9_\\.]*$")) {
            throw new IllegalArgumentException("非法列名: " + col);
        }
        return col;
    }

    /** 值加引号，防SQL注入 */
    static String quoteValue(String val) {
        return "'" + val.replace("'", "''") + "'";
    }

    /** 简版 JSON 解析字段权限 {"hidden":["a","b"],"visible":["c"]} */
    static void parseFieldPermissions(String json, Set<String> hidden, Set<String> visible) {
        extractArray(json, "hidden", hidden);
        extractArray(json, "visible", visible);
    }

    private static void extractArray(String json, String key, Set<String> target) {
        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return;
        int arrStart = json.indexOf("[", keyIdx);
        int arrEnd = json.indexOf("]", arrStart);
        if (arrStart > 0 && arrEnd > arrStart) {
            String arr = json.substring(arrStart + 1, arrEnd);
            for (String item : arr.split(",")) {
                String cleaned = item.trim().replace("\"", "");
                if (!cleaned.isEmpty()) target.add(cleaned);
            }
        }
    }

    /** 简版 JSON 解析操作权限 {"create":false,"delete":true} */
    static void parseActionPermissions(String json, Map<String, Boolean> actions) {
        String content = json.replace("{", "").replace("}", "");
        for (String pair : content.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String k = kv[0].trim().replace("\"", "");
                boolean v = Boolean.parseBoolean(kv[1].trim());
                actions.put(k, v);
            }
        }
    }

    // ======================== 内部类 ========================

    /** 变量上下文 — 提供规则变量值 */
    @Data
    public static class VariableContext {
        Map<String, String> variables = new LinkedHashMap<>();

        static VariableContext from(SecurityUser user) {
            VariableContext ctx = new VariableContext();
            if (user == null) return ctx;

            ctx.put("user.userId",   String.valueOf(user.getUserId()));
            ctx.put("user.username", nvl(user.getUsername()));
            ctx.put("user.realName", nvl(user.getRealName()));
            ctx.put("user.orgId",    nvl(user.getOrgId()));
            ctx.put("user.deptId",   nvl(user.getDeptId()));

            // 组织ID列表（含下级）
            List<Long> managed = user.getManagedOrgIds();
            if (managed != null && !managed.isEmpty()) {
                ctx.put("user.orgIdsAndChildren",
                        String.join(",", managed.stream().map(String::valueOf).toList()));
            } else {
                ctx.put("user.orgIdsAndChildren", "0");
            }

            // 时间变量
            java.time.LocalDate today = java.time.LocalDate.now();
            ctx.put("time.today", today.toString());
            ctx.put("time.thisMonthStart", today.withDayOfMonth(1).toString());
            ctx.put("time.thisMonthEnd",
                    today.withDayOfMonth(today.lengthOfMonth()).toString());
            ctx.put("time.thisQuarterStart",
                    today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1)
                            .withDayOfMonth(1).toString());
            java.time.LocalDate quarterEnd = today
                    .withMonth(((today.getMonthValue() - 1) / 3) * 3 + 3)
                    .withDayOfMonth(1).plusMonths(1).minusDays(1);
            ctx.put("time.thisQuarterEnd", quarterEnd.toString());

            return ctx;
        }

        void put(String key, String value) { variables.put(key, value); }

        private static String nvl(Object obj) {
            return obj != null ? String.valueOf(obj) : "";
        }
    }

    /** 规则求值结果 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleResult {
        public static final RuleResult EMPTY = RuleResult.builder().build();

        @Builder.Default List<String> whereClauses = Collections.emptyList();
        @Builder.Default Set<String> hiddenFields = Collections.emptySet();
        @Builder.Default Set<String> visibleFields = Collections.emptySet();
        @Builder.Default Map<String, Map<String, Boolean>> actionsByEntity = Collections.emptyMap();

        /** 生成完整的 WHERE 子句 */
        public String toWhereClause() {
            if (whereClauses.isEmpty()) return "";
            return String.join(" AND ", whereClauses);
        }
    }
}
