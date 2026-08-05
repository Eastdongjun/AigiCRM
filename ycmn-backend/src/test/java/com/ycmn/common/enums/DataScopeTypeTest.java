package com.ycmn.common.enums;

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
}
