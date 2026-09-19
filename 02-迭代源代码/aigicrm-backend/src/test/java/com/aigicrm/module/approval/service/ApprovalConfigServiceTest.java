package com.aigicrm.module.approval.service;

import com.aigicrm.module.approval.entity.BizApprovalConfig;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 【PR-03】USER 模式解析走 LambdaQueryWrapper<SysUser>,单测需初始化 lambda 缓存
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.aigicrm.module.system.entity.SysUser.class);
    }

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
        stubValidUsers(5L, 6L);
        List<Long> result = ReflectionTestUtils.invokeMethod(svc, "resolveApproverIds", cfg);
        assertEquals(List.of(5L, 6L), result);
    }

    // ===== 【PR-02 2026-09-19】节点实例化统一入口:串行阶段 + 或签/会签落节点 =====

    /** 【PR-03】USER 模式解析需库内有效用户;测试夹具按 id 生成有效集 */
    private void stubValidUsers(Long... ids) {
        List<com.aigicrm.module.system.entity.SysUser> users = new java.util.ArrayList<>();
        for (Long id : ids) {
            com.aigicrm.module.system.entity.SysUser u = new com.aigicrm.module.system.entity.SysUser();
            u.setId(id);
            users.add(u);
        }
        when(userMapper.selectList(any())).thenReturn(users);
    }


    private static BizApprovalConfig stageConfig(long id, int sortOrder, String approverIds, String signMode) {
        BizApprovalConfig c = new BizApprovalConfig();
        c.setId(id);
        c.setFlowType("extend");
        c.setApproverType("USER");
        c.setApproverIds(approverIds);
        c.setSignMode(signMode);
        c.setSortOrder(sortOrder);
        c.setStatus(1);
        return c;
    }

    @Test
    void createNodes_serialStages_firstPendingRestWaiting() {
        stubValidUsers(7L, 8L, 9L);
        when(configMapper.selectByFlowType("extend")).thenReturn(List.of(
                stageConfig(10L, 1, "[7,8]", "ANY"),
                stageConfig(11L, 2, "[9]", "ALL")));

        List<Long> approvers = svc.createNodes(500L, "extend");

        assertEquals(List.of(7L, 8L, 9L), approvers, "跨阶段返回全部审批人");
        org.mockito.ArgumentCaptor<com.aigicrm.module.approval.entity.BizApprovalNode> cap =
                org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.approval.entity.BizApprovalNode.class);
        verify(nodeMapper, times(3)).insert(cap.capture());
        List<com.aigicrm.module.approval.entity.BizApprovalNode> nodes = cap.getAllValues();

        assertEquals(1, nodes.get(0).getNodeOrder(), "首阶段序=1");
        assertEquals("pending", nodes.get(0).getStatus(), "首阶段激活");
        assertEquals("pending", nodes.get(1).getStatus(), "首阶段同组审批人均 pending(阶段内并行)");
        assertEquals("ANY", nodes.get(0).getSignMode(), "或签语义随节点落库");
        assertEquals(10L, nodes.get(0).getConfigId());

        assertEquals(2, nodes.get(2).getNodeOrder(), "第二阶段序=2");
        assertEquals("waiting", nodes.get(2).getStatus(), "串行:后续阶段 waiting,前序完成才激活");
        assertEquals("ALL", nodes.get(2).getSignMode());
        assertEquals(11L, nodes.get(2).getConfigId());
    }

    @Test
    void createNodes_emptyStage_skippedWithoutDeadlock() {
        stubValidUsers(9L);
        when(configMapper.selectByFlowType("extend")).thenReturn(List.of(
                stageConfig(10L, 1, "[]", "ANY"),   // 空审批人阶段:跳过
                stageConfig(11L, 2, "[9]", "ALL")));

        List<Long> approvers = svc.createNodes(501L, "extend");

        assertEquals(List.of(9L), approvers);
        org.mockito.ArgumentCaptor<com.aigicrm.module.approval.entity.BizApprovalNode> cap =
                org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.approval.entity.BizApprovalNode.class);
        verify(nodeMapper, times(1)).insert(cap.capture());
        assertEquals(1, cap.getValue().getNodeOrder(), "空阶段跳过后,有效阶段重新编号为 1");
        assertEquals("pending", cap.getValue().getStatus(), "首个有效阶段直接激活,不留 waiting 死锁");
    }

    @Test
    void createNodes_signModeNormalized_nullDefaultsToAll() {
        stubValidUsers(7L);
        when(configMapper.selectByFlowType("extend")).thenReturn(List.of(
                stageConfig(10L, 1, "[7]", null)));

        svc.createNodes(502L, "extend");

        org.mockito.ArgumentCaptor<com.aigicrm.module.approval.entity.BizApprovalNode> cap =
                org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.approval.entity.BizApprovalNode.class);
        verify(nodeMapper).insert(cap.capture());
        assertEquals("ALL", cap.getValue().getSignMode(), "signMode 空/未知默认会签(保守口径)");
    }

    /** 【PR-03 2026-09-19】USER 模式同样过滤停用/删除用户(与 ROLE/DEPT 同口径),防止节点创建即卡死 */
    @Test
    void resolveApproverIds_usermode_skipsInvalidUsers() {
        BizApprovalConfig cfg = new BizApprovalConfig();
        cfg.setApproverType("USER");
        cfg.setApproverIds("[5,6]");
        // 库内有效集仅用户 5(6 已停用/删除)
        com.aigicrm.module.system.entity.SysUser valid = new com.aigicrm.module.system.entity.SysUser();
        valid.setId(5L);
        when(userMapper.selectList(any())).thenReturn(List.of(valid));

        List<Long> result = ReflectionTestUtils.invokeMethod(svc, "resolveApproverIds", cfg);

        assertEquals(List.of(5L), result, "停用/删除用户不得成为审批人");
    }
}
