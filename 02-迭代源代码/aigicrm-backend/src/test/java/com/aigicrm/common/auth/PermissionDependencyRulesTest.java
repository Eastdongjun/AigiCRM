package com.aigicrm.common.auth;

import com.aigicrm.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 字段/附属码依赖规则（2026-11-15）：只勾子码拒绝；取消父码自动剪子码。 */
class PermissionDependencyRulesTest {

    private static Map<Long, String> codeById() {
        Map<Long, String> m = new HashMap<>();
        m.put(41L, "customer:edit");
        m.put(128L, "customer:date:edit");
        m.put(144L, "customer:invoice-date:edit");
        return m;
    }

    @Test
    void childWithoutParent_isRejected() {
        Set<String> codes = new HashSet<>(Set.of("customer:date:edit"));
        Set<Long> ids = new HashSet<>(Set.of(128L));
        var codeMap = codeById();
        Set<String> noExtra = Set.of();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PermissionDependencyRules.resolve(codes, ids, noExtra, codeMap));
        assertEquals(400, ex.getCode());
        assertEquals("FIELD_PERM_REQUIRED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("customer:edit"));
    }

    @Test
    void childWithParent_isAllowed() {
        Set<String> codes = new HashSet<>(Set.of("customer:edit", "customer:date:edit"));
        Set<Long> ids = new HashSet<>(Set.of(41L, 128L));
        PermissionDependencyRules.resolve(codes, ids, Set.of(), codeById());
        assertEquals(2, codes.size());
        assertTrue(ids.contains(128L));
    }

    @Test
    void removingParent_prunesChild() {
        Set<String> codes = new HashSet<>(Set.of("customer:date:edit"));
        Set<Long> ids = new HashSet<>(Set.of(128L));
        // 旧绑定有父码 → 本次无父码 = 主动取消父码，子码一并移除
        PermissionDependencyRules.resolve(codes, ids, Set.of("customer:edit"), codeById());
        assertFalse(codes.contains("customer:date:edit"));
        assertFalse(ids.contains(128L));
    }

    @Test
    void contactViewRequiresCustomerView() {
        Set<String> codes = new HashSet<>(Set.of("contact:view"));
        var codeMap = codeById();
        Set<Long> noIds = Set.of();
        Set<String> noExtra = Set.of();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PermissionDependencyRules.resolve(codes, noIds, noExtra, codeMap));
        assertEquals(400, ex.getCode());
    }
}
