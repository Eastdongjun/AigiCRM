package com.aigicrm.common.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataScopeTypeTest {
    @Test
    void newLevelsRankByBreadth() {
        assertTrue(DataScopeType.ALL.rank() < DataScopeType.REGION.rank());
        assertTrue(DataScopeType.REGION.rank() < DataScopeType.OFFICE.rank());
        assertTrue(DataScopeType.OFFICE.rank() < DataScopeType.DEPT.rank());
        assertTrue(DataScopeType.DEPT.rank() < DataScopeType.TEAM.rank());
        assertTrue(DataScopeType.TEAM.rank() < DataScopeType.SELF.rank());
    }
    @Test
    void fromCodeHandlesOldAndNew() {
        assertEquals(DataScopeType.DEPT, DataScopeType.fromCode("dept"));
        assertEquals(DataScopeType.TEAM, DataScopeType.fromCode("team"));
        assertEquals(DataScopeType.REGION, DataScopeType.fromCode("region"));
        assertEquals(DataScopeType.SELF, DataScopeType.fromCode(null));
        assertEquals(DataScopeType.SELF, DataScopeType.fromCode("unknown"));
    }

    /**
     * 回归（2026-09-11 修复口径分裂）：旧值 dept_and_children 必须等价于 dept，
     * 不得静默降级为 SELF —— 否则多角色并集会把该角色误算成最窄，
     * 且 SysUserService/SysRoleService 的提权防护（用 rank() 比较）会被绕过。
     * 对应 OrgScopeResolver 的 case "dept_and_children"。
     */
    @Test
    void fromCodeMapsLegacyDeptAndChildrenToDept() {
        assertEquals(DataScopeType.DEPT, DataScopeType.fromCode("dept_and_children"));
        assertEquals(DataScopeType.DEPT.rank(), DataScopeType.fromCode("dept_and_children").rank());
        assertNotEquals(DataScopeType.SELF, DataScopeType.fromCode("dept_and_children"));
    }

    /**
     * 新增等价映射不得移动 ordinal（rank 语义依赖声明序）。
     * 现网声明序（含 2 个 @Deprecated 常量）：ALL(0) CUSTOM(1) REGION(2) OFFICE(3) DEPT(4)
     * ORG_AND_CHILDREN(5) ORG(6) TEAM(7) SELF(8) —— 此处 pin 住作"序不得漂移"的金丝雀。
     */
    @Test
    void legacyMappingDoesNotShiftOrdinals() {
        assertEquals(0, DataScopeType.ALL.rank());
        assertEquals(4, DataScopeType.DEPT.rank());
        assertEquals(7, DataScopeType.TEAM.rank());
        assertEquals(8, DataScopeType.SELF.rank());
        assertTrue(DataScopeType.DEPT.rank() < DataScopeType.TEAM.rank());
        assertTrue(DataScopeType.TEAM.rank() < DataScopeType.SELF.rank());
    }
}
