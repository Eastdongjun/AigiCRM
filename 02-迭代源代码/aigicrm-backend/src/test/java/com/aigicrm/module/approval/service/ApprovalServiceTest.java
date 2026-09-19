package com.aigicrm.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.service.SysUserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.security.SecurityUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 单测环境无 Spring 容器：手动初始化 MyBatis-Plus lambda 缓存（LambdaUpdateWrapper 依赖）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizSeaPool.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizApproval.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                com.aigicrm.module.approval.entity.BizApprovalNode.class);
    }

    /** 【PR-02】approve/reject 阶段语义测试用:需要拿到 SeaService/ConfigMapper 实例做验证 */
    private ApprovalService newApprovalService(BizApprovalMapper approvalMapper, BizApprovalNodeMapper nodeMapper,
                                               SeaService seaService, BizApprovalConfigMapper configMapper) {
        return new ApprovalService(
                configMapper,
                approvalMapper,
                nodeMapper,
                mock(BizApprovalFlowMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class),
                seaService,
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                mock(SysUserService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class));
    }

    private static com.aigicrm.module.approval.entity.BizApprovalNode node(Long id, Long approverId, int order,
                                                                           String signMode, Long configId, String status) {
        com.aigicrm.module.approval.entity.BizApprovalNode n = new com.aigicrm.module.approval.entity.BizApprovalNode();
        n.setId(id);
        n.setApproverId(approverId);
        n.setNodeOrder(order);
        n.setSignMode(signMode);
        n.setConfigId(configId);
        n.setStatus(status);
        return n;
    }

    private static com.aigicrm.module.approval.entity.BizApproval approvalOf(Long id, String type, Long customerId) {
        com.aigicrm.module.approval.entity.BizApproval a = new com.aigicrm.module.approval.entity.BizApproval();
        a.setId(id);
        a.setApprovalType(type);
        a.setCustomerId(customerId);
        a.setApplicantId(7L);
        a.setStatus("reviewing");
        return a;
    }

    private void setCurrentUser(SecurityUser u) {
        // 【修复 2026-08-09】9c6e72a 给捞取加了 sea:claim 方法内校验,测试用户需持该权限
        // (否则 submit("claim",...) 在到达业务分支前就抛"没有公海捞取权限")
        setCurrentUser(u, "sea:claim", "approval:view");
    }

    private void setCurrentUser(SecurityUser u, String... authorities) {
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                u, null,
                java.util.Arrays.stream(authorities)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ApprovalService newService(BizSeaPoolMapper seaPoolMapper, BizApprovalMapper approvalMapper,
                                       BizApprovalNodeMapper nodeMapper) {
        return newService(seaPoolMapper, approvalMapper, nodeMapper, mock(SeaConfigService.class));
    }

    private ApprovalService newService(BizSeaPoolMapper seaPoolMapper, BizApprovalMapper approvalMapper,
                                       BizApprovalNodeMapper nodeMapper, SeaConfigService seaConfigService) {
        // 【PR-03 2026-09-19】submit 现要求"至少一个有效审批人"(零节点=假成功单据封堵),
        // 默认夹具给出配置路径 + 一名有效审批人 7L。
        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService configService = mock(ApprovalConfigService.class);
        when(configService.createNodes(any(), anyString())).thenReturn(java.util.List.of(7L));
        return new ApprovalService(
                configMapper,
                approvalMapper,
                nodeMapper,
                mock(BizApprovalFlowMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                seaPoolMapper,
                mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                seaConfigService,
                mock(SysUserService.class),
                configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class));
    }

    @Test
    void submit_claim_atomicUpdateMisses_rejectsAsTaken() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all"); // 跳过公海池可见性检查，聚焦并发分支
        setCurrentUser(u);

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        // 并发竞争：条件更新 affected=0（他人已锁定）
        when(seaPoolMapper.update(isNull(), any())).thenReturn(0);

        ApprovalService svc = newService(seaPoolMapper, mock(BizApprovalMapper.class), mock(BizApprovalNodeMapper.class));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("claim", 42L, null, null));
        assertTrue(ex.getMessage().contains("已被他人申请"));
    }

    @Test
    void submit_claim_atomicUpdateSucceeds_proceeds() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u);

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        when(seaPoolMapper.update(isNull(), any())).thenReturn(1);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        // 锁定更新必须携带 status='available' 条件（原子性）
        verify(seaPoolMapper, times(1)).update(isNull(), any());
    }

    @Test
    void submit_claim_withoutPoolPermOrLegacyAuthority_rejectsBeforeLockingPool() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u, "approval:view");

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setPoolId(9L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);

        SeaConfigService seaConfigService = mock(SeaConfigService.class);
        when(seaConfigService.hasPoolPerm(any(), anyString(), any())).thenReturn(false);

        ApprovalService svc = newService(
                seaPoolMapper,
                mock(BizApprovalMapper.class),
                mock(BizApprovalNodeMapper.class),
                seaConfigService);

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("claim", 42L, null, null));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("没有公海捞取权限"));
        verify(seaPoolMapper, never()).update(isNull(), any());
    }

    @Test
    void submit_claim_poolLevelClaimPermission_allowsWithoutLegacyAuthority() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u, "approval:view");

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setPoolId(9L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        when(seaPoolMapper.update(isNull(), any())).thenReturn(1);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        SeaConfigService seaConfigService = mock(SeaConfigService.class);
        when(seaConfigService.hasPoolPerm(any(), anyString(), any())).thenReturn(true);

        ApprovalService svc = newService(
                seaPoolMapper,
                approvalMapper,
                mock(BizApprovalNodeMapper.class),
                seaConfigService);

        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        verify(seaPoolMapper, times(1)).update(isNull(), any());
    }

    @Test
    void submit_claim_legacySeaClaimAuthority_allowsWhenPoolLevelPermissionMissing() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u, "sea:claim", "approval:view");

        BizSeaPool pool = new BizSeaPool();
        pool.setId(3L);
        pool.setPoolId(9L);
        pool.setCustomerId(42L);
        pool.setStatus("available");

        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectOne(any())).thenReturn(pool);
        when(seaPoolMapper.update(isNull(), any())).thenReturn(1);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        SeaConfigService seaConfigService = mock(SeaConfigService.class);
        when(seaConfigService.hasPoolPerm(any(), anyString(), any())).thenReturn(false);

        ApprovalService svc = newService(
                seaPoolMapper,
                approvalMapper,
                mock(BizApprovalNodeMapper.class),
                seaConfigService);

        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        verify(seaPoolMapper, times(1)).update(isNull(), any());
    }

    @Test
    void statistics_nonAdmin_scopesCounts() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(Collections.singletonList("sales_engineer"));
        u.setDataScope("self");
        setCurrentUser(u);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(nodeMapper.selectCount(any())).thenReturn(1L);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(3L);

        ApprovalService svc = newService(mock(BizSeaPoolMapper.class), approvalMapper, nodeMapper);
        Map<String, Long> stats = svc.statistics(7L);

        assertEquals(1L, stats.get("pending"));
        assertEquals(3L, stats.get("mine"));
        // mine/reviewing/all/approvedThisMonth/rejectedThisMonth 共 5 次，均带数据范围过滤
        verify(approvalMapper, times(5)).selectCount(any());
    }

    /** 回归：参与审批列表为空时不得生成 IN ()（原代码无空守卫导致 SQL 语法错误，生产已出故障） */
    @Test
    void statistics_emptyInvolved_noEmptyInClause() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(Collections.singletonList("sales_engineer"));
        u.setDataScope("self");
        setCurrentUser(u);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(Collections.emptyList()); // 无参与审批
        when(nodeMapper.selectCount(any())).thenReturn(1L);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(3L);

        ApprovalService svc = newService(mock(BizSeaPoolMapper.class), approvalMapper, nodeMapper);
        svc.statistics(7L);
        @SuppressWarnings("unchecked")

        ArgumentCaptor<LambdaQueryWrapper<BizApproval>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(approvalMapper, times(5)).selectCount(cap.capture());
        for (LambdaQueryWrapper<BizApproval> w : cap.getAllValues()) {
            String sql = w.getSqlSegment();
            assertTrue(sql == null || !sql.contains("IN ()"), "空参与列表不得生成 IN ()，实际: " + sql);
        }
    }

    // ===== 【PR-02 2026-09-19】或签/会签/串行统一执行 =====

    /** 或签(ANY):任一人通过即整单通过,同阶段其余待办被取消,业务回写一次 */
    @Test
    void approve_anySignMode_firstApprovalFinalizesAndCancelsSibling() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(11L, "claim", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(11L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(11L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        // 库内已流转后的状态:本人 approved + 同阶段兄弟 pending(ANY)
        com.aigicrm.module.approval.entity.BizApprovalNode mine =
                node(101L, 7L, 1, "ANY", 10L, "approved");
        com.aigicrm.module.approval.entity.BizApprovalNode sibling =
                node(102L, 8L, 1, "ANY", 10L, "pending");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(101L, 7L, 1, "ANY", 10L, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine, sibling));

        SeaService seaService = mock(SeaService.class);
        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, seaService, mock(BizApprovalConfigMapper.class));

        assertDoesNotThrow(() -> svc.approve(11L, "同意"));
        // 同阶段兄弟待办被取消(或签完成)
        verify(nodeMapper, times(2)).update(isNull(), any()); // 1=本人条件流转 2=取消兄弟
        // 整单通过(条件更新胜出) + 业务回写恰好一次
        verify(approvalMapper, times(1)).update(isNull(), any());
        verify(seaService, times(1)).completeClaim(42L, 7L);
    }

    /** 会签(ALL):一人通过后仍 reviewing,全员通过才整单通过 */
    @Test
    void approve_allSignMode_stillReviewingUntilStageAllApproved() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(12L, "extend", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(12L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(12L)).thenReturn(approval);

        com.aigicrm.module.approval.entity.BizApprovalNode mine = node(201L, 7L, 1, "ALL", 10L, "approved");
        com.aigicrm.module.approval.entity.BizApprovalNode other = node(202L, 8L, 1, "ALL", 10L, "pending");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(201L, 7L, 1, "ALL", 10L, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine, other));

        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, mock(SeaService.class), mock(BizApprovalConfigMapper.class));

        assertDoesNotThrow(() -> svc.approve(12L, "同意"));
        // 本阶段仍有 pending → 不整单通过、不取消任何节点
        verify(nodeMapper, times(1)).update(isNull(), any());
        verify(approvalMapper, never()).update(isNull(), any());
    }

    /** 串行:第一阶段完成激活第二阶段(waiting→pending),整单不通过,currentNode 指向新阶段 */
    @Test
    void approve_serialStages_activatesNextWaitingStage() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(13L, "extend", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(13L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(13L)).thenReturn(approval);

        com.aigicrm.module.approval.entity.BizApprovalNode stage1 = node(301L, 7L, 1, "ALL", 10L, "approved");
        com.aigicrm.module.approval.entity.BizApprovalNode stage2 = node(302L, 9L, 2, "ALL", 11L, "waiting");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(301L, 7L, 1, "ALL", 10L, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(stage1, stage2));

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        com.aigicrm.module.approval.entity.BizApprovalConfig cfg = new com.aigicrm.module.approval.entity.BizApprovalConfig();
        cfg.setId(11L);
        cfg.setFlowName("二级审批");
        when(configMapper.selectById(11L)).thenReturn(cfg);

        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, mock(SeaService.class), configMapper);

        assertDoesNotThrow(() -> svc.approve(13L, "同意"));
        // 本人条件流转 + 激活下一阶段
        verify(nodeMapper, times(2)).update(isNull(), any());
        // 整单不通过,但 currentNode 更新到新阶段
        verify(approvalMapper, never()).update(isNull(), any());
        verify(approvalMapper, times(1)).updateById(any(com.aigicrm.module.approval.entity.BizApproval.class));
        assertEquals("二级审批", approval.getCurrentNode());
    }

    /** 重复点击/并发:条件流转 affected=0 → 拒绝,结果唯一 */
    @Test
    void approve_doubleClick_rejectedAsAlreadyProcessed() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(14L)).thenReturn(approvalOf(14L, "extend", 42L));
        when(approvalMapper.selectByIdForUpdate(14L)).thenReturn(approvalOf(14L, "extend", 42L));

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(401L, 7L, 1, "ALL", 10L, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(0); // 并发竞争:他人已处理

        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, mock(SeaService.class), mock(BizApprovalConfigMapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(14L, "同意"));
        assertTrue(ex.getMessage().contains("已处理"));
        verify(approvalMapper, never()).update(isNull(), any());
    }

    /** 存量在途单(节点无 configId)维持原扁平会签口径:全部 pending 清零即整单通过 */
    @Test
    void approve_legacyNodes_keepFlatAllSemantics() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(15L, "claim", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(15L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(15L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        com.aigicrm.module.approval.entity.BizApprovalNode mine = node(501L, 7L, 1, null, null, "approved");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(501L, 7L, 1, null, null, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine));

        SeaService seaService = mock(SeaService.class);
        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, seaService, mock(BizApprovalConfigMapper.class));

        assertDoesNotThrow(() -> svc.approve(15L, "同意"));
        // 仅本人条件流转一次;整单通过并回写业务
        verify(nodeMapper, times(1)).update(isNull(), any());
        verify(approvalMapper, times(1)).update(isNull(), any());
        verify(seaService, times(1)).completeClaim(42L, 7L);
    }

    /** 驳回级联:串行未激活的 waiting 节点一并作废 */
    @Test
    void reject_cascadesWaitingNodes() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(16L, "extend", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(16L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(16L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        com.aigicrm.module.approval.entity.BizApprovalNode waiting = node(602L, 9L, 2, "ALL", 11L, "waiting");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(601L, 7L, 1, "ALL", 10L, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(waiting));

        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, mock(SeaService.class), mock(BizApprovalConfigMapper.class));

        assertDoesNotThrow(() -> svc.reject(16L, "驳回"));
        // waiting 节点被作废(updateById 落 rejected/cancelled)
        verify(nodeMapper, times(1)).updateById(argThat(
                (com.aigicrm.module.approval.entity.BizApprovalNode n) ->
                        "rejected".equals(n.getStatus()) && "cancelled".equals(n.getAction())));
    }

    // ===== 【PR-03 2026-09-19】零审批人假成功单据封堵 =====

    /** 配置路径:配置存在但解析不出任何有效审批人 → 拒绝提交(不落零节点卡死单) */
    @Test
    void submit_configPath_noValidApprovers_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u);

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService configService = mock(ApprovalConfigService.class);
        when(configService.createNodes(any(), anyString())).thenReturn(java.util.List.of());

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, mock(BizApprovalNodeMapper.class),
                mock(BizApprovalFlowMapper.class), mock(BizCustomerMapper.class),
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(SysUserService.class), configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("extend", 42L, null, null));
        assertTrue(ex.getMessage().contains("无有效审批人"), "实际: " + ex.getMessage());
    }

    /** legacy 流程路径:无流程配置且无直属领导 → 零节点 → 同口径拒绝(此前静默落永久卡死单) */
    @Test
    void submit_legacyPath_zeroNodes_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u);

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(0L);
        BizApprovalFlowMapper flowMapper = mock(BizApprovalFlowMapper.class);
        when(flowMapper.selectList(any())).thenReturn(java.util.List.of()); // 无流程配置
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(7L)).thenReturn(null); // 申请人查不到 → 无领导

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString())).thenReturn(0);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, nodeMapper,
                flowMapper, mock(BizCustomerMapper.class),
                userMapper, mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(SysUserService.class), mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("extend", 42L, null, null));
        assertTrue(ex.getMessage().contains("未配置或无有效审批人"), "实际: " + ex.getMessage());
        verify(nodeMapper, never()).insert(any(com.aigicrm.module.approval.entity.BizApprovalNode.class));
    }
}
