package com.ycmn.common.engine;

import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void quoteValue_escapesSingleQuotes() {
        assertEquals("'a''b'", PermissionRuleEngine.quoteValue("a'b"));
    }

    @Test
    void escapeColumn_rejectsUnsafeColumnName() {
        assertThrows(IllegalArgumentException.class,
                () -> PermissionRuleEngine.escapeColumn("org_id; DROP TABLE"));
    }
}
