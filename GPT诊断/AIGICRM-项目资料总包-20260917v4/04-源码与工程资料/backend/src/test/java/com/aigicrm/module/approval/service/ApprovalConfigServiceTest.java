package com.aigicrm.module.approval.service;

import com.aigicrm.module.approval.entity.BizApprovalConfig;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** S3 回归(2026-08-09): 审批 ROLE 审批人解析改 sys_user_role JOIN 单查，消除"全量用户+逐用户查角色"的全表扫描+N+1。 */
class ApprovalConfigServiceTest {

    private final BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final ApprovalConfigService svc = new ApprovalConfigService(configMapper, nodeMapper, userMapper, roleMapper);

    @Test
    void resolveApproverIds_rolemode_usesJoinQueryNotFullScan() {
        BizApprovalConfig cfg = new BizApprovalConfig();
        cfg.setApproverType("ROLE");
        cfg.setApproverIds("[7]");
        // 【2026-08-11 P1 关联修复】审批 ROLE 解析走 selectActiveUserIdsByRoleId(过滤角色 status=1)
        when(roleMapper.selectActiveUserIdsByRoleId(7L)).thenReturn(List.of(11L, 22L));

        List<Long> result = ReflectionTestUtils.invokeMethod(svc, "resolveApproverIds", cfg);

        assertEquals(List.of(11L, 22L), result);
        verify(roleMapper).selectActiveUserIdsByRoleId(7L);
        // 旧实现全表 selectList 用户 + 逐用户 selectRolesByUserId;新实现不再触发(用户量大时数千条 SQL→2 条)
        verify(userMapper, never()).selectList(any());
        verify(roleMapper, never()).selectRolesByUserId(any());
    }

    @Test
    void resolveApproverIds_rolemode_invalidRoleLogsAndSkips() {
        BizApprovalConfig cfg = new BizApprovalConfig();
        cfg.setApproverType("ROLE");
        cfg.setApproverIds("[9]");
        when(roleMapper.selectActiveUserIdsByRoleId(9L)).thenThrow(new RuntimeException("db down"));

        List<Long> result = ReflectionTestUtils.invokeMethod(svc, "resolveApproverIds", cfg);

        assertEquals(List.of(), result); // 单角色解析失败只跳过该角色,不抛整个审批
    }

    @Test
    void resolveApproverIds_usermode_passesThrough() {
        BizApprovalConfig cfg = new BizApprovalConfig();
        cfg.setApproverType("USER");
        cfg.setApproverIds("[5,6]");
        List<Long> result = ReflectionTestUtils.invokeMethod(svc, "resolveApproverIds", cfg);
        assertEquals(List.of(5L, 6L), result);
    }
}
