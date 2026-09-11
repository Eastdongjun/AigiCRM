package com.aigicrm.common.engine;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysPermissionRule;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRuleEngineTest {

    private SysPermissionRule rule(String targetEntity, String ruleType,
                                   String targetColumn, String operator,
                                   String ruleValue, Integer status) {
        SysPermissionRule r = new SysPermissionRule();
        r.setTargetEntity(targetEntity);
        r.setRuleType(ruleType);
        r.setTargetColumn(targetColumn);
        r.setOperator(operator);
        r.setRuleValue(ruleValue);
        r.setStatus(status);
        return r;
    }

    private SecurityUser user() {
        SecurityUser u = new SecurityUser();
        u.setUserId(5L);
        u.setOrgId(1L);
        u.setManagedOrgIds(Arrays.asList(1L, 2L, 3L));
        return u;
    }

    @Test
    void evaluateForEntity_filtersByTargetEntity() {
        List<SysPermissionRule> rules = Arrays.asList(
                rule("incentive", "org_filter", "org_id", "in", "${user.orgIdsAndChildren}", 1),
                rule("customer", "column_filter", "owner_id", "eq", "${user.userId}", 1)
        );
        assertEquals("owner_id = '5'",
                PermissionRuleEngine.evaluateForEntity(rules, user(), "customer"));
        assertEquals("org_id IN (1,2,3)",
                PermissionRuleEngine.evaluateForEntity(rules, user(), "incentive"));
    }

    @Test
    void evaluateForEntity_skipsInactiveRules() {
        List<SysPermissionRule> rules = Collections.singletonList(
                rule("customer", "column_filter", "owner_id", "eq", "${user.userId}", 0));
        assertEquals("", PermissionRuleEngine.evaluateForEntity(rules, user(), "customer"));
    }

    @Test
    void evaluateForEntity_emptyRules_returnsEmpty() {
        assertEquals("", PermissionRuleEngine.evaluateForEntity(null, user(), "customer"));
        assertEquals("", PermissionRuleEngine.evaluateForEntity(Collections.emptyList(), user(), "customer"));
    }

    @Test
    void evaluate_combinesOrgAndColumnFilters() {
        List<SysPermissionRule> rules = Arrays.asList(
                rule("customer", "org_filter", "org_id", "in", "${user.orgIdsAndChildren}", 1),
                rule("customer", "column_filter", "owner_id", "eq", "${user.userId}", 1)
        );
        PermissionRuleEngine.RuleResult result = PermissionRuleEngine.evaluate(rules, user());
        assertEquals("org_id IN (1,2,3) AND owner_id = '5'", result.toWhereClause());
    }

    @Test
    void evaluate_orgFilterFallsBackToZeroWithoutManagedOrgs() {
        SecurityUser u = user();
        u.setManagedOrgIds(Collections.emptyList());
        List<SysPermissionRule> rules = Collections.singletonList(
                rule("customer", "org_filter", "org_id", "in", "${user.orgIdsAndChildren}", 1));
        PermissionRuleEngine.RuleResult result = PermissionRuleEngine.evaluate(rules, u);
        assertEquals("org_id IN (0)", result.toWhereClause());
    }

    @Test
    void evaluate_actionControl_groupsByTargetEntity() {
        List<SysPermissionRule> rules = Arrays.asList(
                rule("customer", "action_control", null, null, "{\"create\":false,\"delete\":true}", 1),
                rule("incentive", "action_control", null, null, "{\"delete\":false}", 1)
        );
        PermissionRuleEngine.RuleResult result = PermissionRuleEngine.evaluate(rules, user());
        Map<String, Map<String, Boolean>> actions = result.getActionsByEntity();
        assertEquals(Boolean.FALSE, actions.get("customer").get("create"));
        assertEquals(Boolean.TRUE, actions.get("customer").get("delete"));
        assertEquals(Boolean.FALSE, actions.get("incentive").get("delete"));
        // 实体间不互相污染：customer 无 delete=false 之外的其他实体动作
        assertEquals(2, actions.get("customer").size());
        assertEquals(1, actions.get("incentive").size());
    }

    @Test
    void evaluate_actionControl_ignoresRuleWithoutTargetEntity() {
        List<SysPermissionRule> rules = Collections.singletonList(
                rule(null, "action_control", null, null, "{\"create\":false}", 1));
        PermissionRuleEngine.RuleResult result = PermissionRuleEngine.evaluate(rules, user());
        // 未声明实体的 action_control 不生效，防止全局一刀切误伤
        assertTrue(result.getActionsByEntity().isEmpty());
    }

    @Test
    void evaluate_noActionControlRules_hasEmptyActions() {
        PermissionRuleEngine.RuleResult result =
                PermissionRuleEngine.evaluate(Collections.emptyList(), user());
        assertTrue(result.getActionsByEntity().isEmpty());
    }

    @Test
    void quoteValue_escapesSingleQuotes() {
        assertEquals("'a''b'", PermissionRuleEngine.quoteValue("a'b"));
    }

    @Test
    void escapeColumn_rejectsUnsafeColumnName() {
        assertThrows(IllegalArgumentException.class,
                () -> PermissionRuleEngine.escapeColumn("org_id; DROP TABLE"));
    }

    /** 安全 2026-08-08：in 操作符非数字值必须转义，注入片段被锁在引号内 */
    @Test
    void quoteInList_neutralizesSqlInjection() {
        String sql = PermissionRuleEngine.quoteInList("1,2'); DROP TABLE users;--");
        // 纯数字保留不加引号；注入片段整体被单引号包裹且单引号转义 → DROP 不再可执行
        assertTrue(sql.startsWith("1,"), "纯数字不应加引号: " + sql);
        assertTrue(sql.contains("'2''); DROP TABLE users;--'"),
                "注入片段应被转义包裹, 实际: " + sql);
    }

    // ======================== 安全修复 2026-08-14：反斜杠转义 + 变量白名单 ========================

    /** MySQL 默认 sql_mode 下反斜杠也是转义符：\ 必须先转义成 \\，再转义 ' 为 ''，
     *  否则 \' 载荷可闭合引号逃逸字符串 */
    @Test
    void quoteValue_escapesBackslashBeforeQuote() {
        assertEquals("'a\\\\b'", PermissionRuleEngine.quoteValue("a\\b"));       // \ → \\
        assertEquals("'\\\\'''", PermissionRuleEngine.quoteValue("\\'"));        // \' → \\''
        assertEquals("'\\\\\\\\'''", PermissionRuleEngine.quoteValue("\\\\'"));  // \\' → \\\\''
        assertEquals("''' OR 1=1 --'", PermissionRuleEngine.quoteValue("' OR 1=1 --"));
        assertEquals("'\\\\''; DROP TABLE users;--'",
                PermissionRuleEngine.quoteValue("\\'; DROP TABLE users;--"));
    }

    /** IN 列表中反斜杠+引号载荷也必须被锁在字符串内 */
    @Test
    void quoteInList_escapesBackslashPayload() {
        String sql = PermissionRuleEngine.quoteInList("1,2\\'); DROP TABLE users;--");
        assertTrue(sql.startsWith("1,"), "纯数字不应加引号: " + sql);
        assertTrue(sql.contains("'2\\\\''); DROP TABLE users;--'"),
                "反斜杠+引号载荷应整体锁在字符串内, 实际: " + sql);
    }

    /** 变量白名单：未支持的变量键必须抛业务异常，禁止静默原样输出进 SQL */
    @Test
    void resolve_rejectsUnsupportedVariableKeys() {
        PermissionRuleEngine.VariableContext ctx = PermissionRuleEngine.VariableContext.from(user());
        assertThrows(BusinessException.class,
                () -> PermissionRuleEngine.resolve("owner_id = ${user.password}", ctx));
        assertThrows(BusinessException.class,
                () -> PermissionRuleEngine.resolve("org_id = ${user.officeId}", ctx));
        assertThrows(BusinessException.class,
                () -> PermissionRuleEngine.resolve("create_time < ${time.yesterday}", ctx));
    }

    /** 变量白名单：支持的 user.* 键正常替换，null 模板原样返回 */
    @Test
    void resolve_replacesSupportedVariableKeys() {
        PermissionRuleEngine.VariableContext ctx = PermissionRuleEngine.VariableContext.from(user());
        assertEquals("owner_id = 5 AND org_id = 1",
                PermissionRuleEngine.resolve("owner_id = ${user.userId} AND org_id = ${user.orgId}", ctx));
        assertNull(PermissionRuleEngine.resolve(null, ctx));
    }

    /** 全链路：规则值含未支持变量时 evaluate 直接抛业务异常 */
    @Test
    void evaluateForEntity_unsupportedVariableThrows() {
        List<SysPermissionRule> rules = Collections.singletonList(
                rule("customer", "column_filter", "owner_id", "eq", "${user.officeId}", 1));
        assertThrows(BusinessException.class,
                () -> PermissionRuleEngine.evaluateForEntity(rules, user(), "customer"));
    }

    /** 全链路：规则值含反斜杠+引号载荷时被安全转义，无法逃逸字符串 */
    @Test
    void evaluate_quotesInjectedRuleValue() {
        List<SysPermissionRule> rules = Collections.singletonList(
                rule("customer", "column_filter", "owner_id", "eq", "\\'; DROP TABLE users;--", 1));
        assertEquals("owner_id = '\\\\''; DROP TABLE users;--'",
                PermissionRuleEngine.evaluateForEntity(rules, user(), "customer"));
    }

    /** time.* 白名单键正常替换，BETWEEN 结构正确 */
    @Test
    void evaluate_timeRangeWithSupportedTimeVariables() {
        List<SysPermissionRule> rules = Collections.singletonList(
                rule("customer", "time_range", "create_time", "between",
                        "${time.thisMonthStart},${time.thisMonthEnd}", 1));
        String sql = PermissionRuleEngine.evaluateForEntity(rules, user(), "customer");
        assertTrue(sql.startsWith("create_time BETWEEN '"), "实际: " + sql);
        assertTrue(sql.contains("' AND '"), "实际: " + sql);
        assertTrue(sql.endsWith("'"), "实际: " + sql);
    }
}
