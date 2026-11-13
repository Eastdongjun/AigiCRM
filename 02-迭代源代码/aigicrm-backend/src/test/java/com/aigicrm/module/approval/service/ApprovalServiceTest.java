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
import org.mockito.MockedStatic;
import com.aigicrm.security.SecurityUtils;
import static org.mockito.Mockito.mockStatic;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
        // transfer 执行分支用 LambdaUpdateWrapper<BizCustomer> 显式同步部门(2026-10-08 修复)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                com.aigicrm.module.customer.entity.BizCustomer.class);
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
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));
    }

    /** 【2026-11-02 副本收敛】延期公式已收敛进 CustomerService.applyExtendApproval(三线/365),
     *  extend 执行用例注入真实例(其余依赖全 mock; configService 返回 null → 公式走默认 365/180)。 */
    private static com.aigicrm.module.customer.service.CustomerService realCustomerService(BizCustomerMapper customerMapper) {
        return new com.aigicrm.module.customer.service.CustomerService(
                customerMapper,
                null,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizInvoiceMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
        // 【L1 2026-10-12】申请人=8 与审批人=7 分离: 自审自批闸门(approve 侧)生效后,
        // 同人申请+审批的便捷设定会误触发新守卫
        a.setApplicantId(8L);
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
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0); // 【G2】取号带租户参数

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        // 锁定更新必须携带 status='available' 条件（原子性）
        verify(seaPoolMapper, times(1)).update(isNull(), any());
    }

    @Test
    void submit_claim_atomicLockWritesClaimDate_forTimeoutRelease() {
        // 【体检 H5 2026-10-12】锁定必须写 claim_date: 超时释放任务 releaseStaleClaimLocks 以
        // lt(claim_date, deadline) 筛超时行, 锁定不写时间戳则 SQL 中 NULL 永不命中——无人审批的锁
        // 永久 pending_approval 锁死池行(体检 H5 断链, 此处契约钉防回归)
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizSeaPool>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(seaPoolMapper, times(1)).update(isNull(), captor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizSeaPool>) captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("claim_date"), "锁定更新必须写入 claim_date(超时释放依赖), 实际: " + sqlSet);
        assertTrue(sqlSet.contains("claim_user_id"), "锁定更新必须写入 claim_user_id, 实际: " + sqlSet);
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0); // 【G2】取号带租户参数

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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0); // 【G2】取号带租户参数

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

    // ====== 【2026-09-23 转移校验前移】submit("transfer") 接收人 fail-fast 测试组 ======

    private ApprovalService newTransferService(SysUserMapper userMapper,
                                               org.springframework.jdbc.core.JdbcTemplate jdbc,
                                               BizApprovalMapper approvalMapper) {
        // 【P0 2026-10-17】transfer 提交侧新增客户归属校验: 默认给可查看的正常客户,
        // 负向归属用例见 TransferSubmitOwnershipTest。
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer guardCustomer =
                new com.aigicrm.module.customer.entity.BizCustomer();
        guardCustomer.setId(42L);
        guardCustomer.setStatus("normal");
        when(customerMapper.selectById(42L)).thenReturn(guardCustomer);
        com.aigicrm.common.engine.CustomerAccessService accessService =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(accessService.canView(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        return newTransferService(userMapper, jdbc, approvalMapper, customerMapper, accessService);
    }

    private ApprovalService newTransferService(SysUserMapper userMapper,
                                               org.springframework.jdbc.core.JdbcTemplate jdbc,
                                               BizApprovalMapper approvalMapper,
                                               BizCustomerMapper customerMapper) {
        return newTransferService(userMapper, jdbc, approvalMapper, customerMapper,
                mock(com.aigicrm.common.engine.CustomerAccessService.class));
    }

    private ApprovalService newTransferService(SysUserMapper userMapper,
                                               org.springframework.jdbc.core.JdbcTemplate jdbc,
                                               BizApprovalMapper approvalMapper,
                                               BizCustomerMapper customerMapper,
                                               com.aigicrm.common.engine.CustomerAccessService accessService) {
        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService configService = mock(ApprovalConfigService.class);
        when(configService.createNodes(any(), anyString())).thenReturn(java.util.List.of(7L));
        return new ApprovalService(
                configMapper,
                approvalMapper,
                mock(BizApprovalNodeMapper.class),
                mock(BizApprovalFlowMapper.class),
                customerMapper,
                userMapper,
                mock(SysUserRoleMapper.class),
                mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class),
                mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                accessService,
                mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                jdbc,
                mock(com.aigicrm.security.SecurityAuditService.class));
    }

    private static com.aigicrm.module.system.entity.SysUser recipient(Long id, Integer status, Integer deleted) {
        com.aigicrm.module.system.entity.SysUser u = new com.aigicrm.module.system.entity.SysUser();
        u.setId(id);
        u.setStatus(status);
        u.setDeleted(deleted);
        u.setUsername("rcv");
        u.setRealName("接收人");
        return u;
    }

    @Test
    void submit_transfer_missingRecipient_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        setCurrentUser(u);
        ApprovalService svc = newTransferService(mock(SysUserMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class), mock(BizApprovalMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, null));
        assertTrue(ex.getMessage().contains("缺少接收人"));
        ex = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, "{\"foo\":1}"));
        assertTrue(ex.getMessage().contains("缺少接收人"));
    }

    @Test
    void submit_extendOrTransfer_seaCustomer_rejectsBeforeCreatingApproval() {
        // 【体检 P1 2026-10-12】公海守卫(提交侧): 池内客户禁发起延期/转移——审批通过会把客户
        // 拉回私海/盲改属主而池行残留(双归属); 私海客户不受影响
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        setCurrentUser(u);

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer sea = new com.aigicrm.module.customer.entity.BizCustomer();
        sea.setId(42L);
        sea.setStatus("sea");
        when(customerMapper.selectById(42L)).thenReturn(sea);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        ApprovalService svc = newTransferService(mock(SysUserMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class), approvalMapper, customerMapper);

        BusinessException extendEx = assertThrows(BusinessException.class,
                () -> svc.submit("extend", 42L, null, null));
        assertTrue(extendEx.getMessage().contains("公海"), "延期守卫: " + extendEx.getMessage());
        BusinessException transferEx = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, "{\"newOwnerId\":9}"));
        assertTrue(transferEx.getMessage().contains("公海"), "转移守卫: " + transferEx.getMessage());
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void submit_transfer_disabledRecipient_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        setCurrentUser(u);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(9L)).thenReturn(recipient(9L, 0, 0));
        ApprovalService svc = newTransferService(userMapper,
                mock(org.springframework.jdbc.core.JdbcTemplate.class), mock(BizApprovalMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, "{\"newOwnerId\":9}"));
        assertTrue(ex.getMessage().contains("接收人不存在或已停用"));
    }

    @Test
    void submit_transfer_deletedRecipient_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        setCurrentUser(u);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(9L)).thenReturn(recipient(9L, 1, 1));
        ApprovalService svc = newTransferService(userMapper,
                mock(org.springframework.jdbc.core.JdbcTemplate.class), mock(BizApprovalMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, "{\"newOwnerId\":9}"));
        assertTrue(ex.getMessage().contains("接收人不存在或已停用"));
    }

    @Test
    void submit_transfer_validRecipientSameGroup_proceeds() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        u.setCurrentGroupId(1L);
        setCurrentUser(u);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(9L)).thenReturn(recipient(9L, 1, 0));
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(java.util.List.of(7L, 9L));
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);
        ApprovalService svc = newTransferService(userMapper, jdbc, approvalMapper);
        assertDoesNotThrow(() -> svc.submit("transfer", 42L, null, "{\"newOwnerId\":9}"));
        verify(approvalMapper, times(1)).insert(any(BizApproval.class));
    }

    @Test
    void submit_transfer_crossGroupRecipient_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        u.setCurrentGroupId(1L);
        setCurrentUser(u);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectById(9L)).thenReturn(recipient(9L, 1, 0));
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(java.util.List.of(7L));
        ApprovalService svc = newTransferService(userMapper, jdbc, mock(BizApprovalMapper.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.submit("transfer", 42L, null, "{\"newOwnerId\":9}"));
        assertTrue(ex.getMessage().contains("接收人不在当前集团"));
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
        // mine/reviewing/all/approvedThisMonth/rejectedThisMonth + approvedTotal/rejectedTotal 共 7 次，均带数据范围过滤
        // 【2026-10-15】新增全量键: 页签角标与列表 total 口径一致(INV-12)
        verify(approvalMapper, times(7)).selectCount(any());
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
        verify(approvalMapper, times(7)).selectCount(cap.capture());
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
        verify(seaService, times(1)).completeClaim(42L, 8L);
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
        verify(seaService, times(1)).completeClaim(42L, 8L);
    }

    @Test
    void approve_transfer_clearsDeptWhenRecipientHasNone() {
        // 【修复 2026-10-08 需求对齐】转移时接收人无部门 → 客户部门必须显式清空:
        // updateById 的 NOT_NULL 策略不写 null 字段,原"有部门才 set"会残留旧负责人部门,
        // ORG 锚点(biz_customer.dept_id)据此错误关联;与创建/编辑路径 2026-08-12 A3 同口径。
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(18L, "transfer", 42L);
        approval.setExtraData("{\"newOwnerId\":9}");
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(18L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(18L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(501L, 7L, 1, null, null, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(node(501L, 7L, 1, null, null, "approved")));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer customer = new com.aigicrm.module.customer.entity.BizCustomer();
        customer.setId(42L);
        customer.setDeptId(66L); // 旧负责人部门(残留源)
        when(customerMapper.selectById(42L)).thenReturn(customer);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        com.aigicrm.module.system.entity.SysUser newOwner = new com.aigicrm.module.system.entity.SysUser();
        newOwner.setId(9L);
        newOwner.setStatus(1);
        newOwner.setDeleted(0);
        newOwner.setDeptId(null); // 接收人无部门
        when(userMapper.selectById(9L)).thenReturn(newOwner);

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapper, userMapper,
                mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(SysUserService.class), mock(com.aigicrm.module.customer.service.CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class), mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        assertDoesNotThrow(() -> svc.approve(18L, "同意"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizCustomer>> wcap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(customerMapper).update(isNull(), wcap.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<com.aigicrm.module.customer.entity.BizCustomer, ?, ?> w =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<com.aigicrm.module.customer.entity.BizCustomer, ?, ?>) wcap.getValue();
        String sqlSet = w.getSqlSet();
        assertTrue(sqlSet.contains("owner_id"), "转移应写 owner_id: " + sqlSet);
        assertTrue(sqlSet.contains("transfer_date"), "转移应写 transfer_date: " + sqlSet);
        assertTrue(sqlSet.contains("dept_id"), "必须显式 set dept_id(接收人无部门时清空): " + sqlSet);
        assertTrue(w.getParamNameValuePairs().containsValue(null), "接收人无部门时 dept_id 值应为 null: " + w.getParamNameValuePairs());
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0); // 【G2】取号带租户参数

        // 【P0 2026-10-16】extend 守卫补归属校验后, 此用例需客户存在且可查看才能到达无审批人守卫
        BizCustomerMapper customerMapperCfg = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer cCfg = new com.aigicrm.module.customer.entity.BizCustomer();
        cCfg.setId(42L); cCfg.setStatus("normal");
        when(customerMapperCfg.selectById(42L)).thenReturn(cCfg);
        com.aigicrm.common.engine.CustomerAccessService accessCfg = mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(accessCfg.canView(42L)).thenReturn(true);
        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, mock(BizApprovalNodeMapper.class),
                mock(BizApprovalFlowMapper.class), customerMapperCfg,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                accessCfg,
                mock(SysUserService.class), mock(com.aigicrm.module.customer.service.CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class), configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0); // 【G2】取号带租户参数

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        // 【P0 2026-10-16】extend 守卫补归属校验后, 此用例需客户存在且可查看才能到达零节点守卫
        BizCustomerMapper customerMapper42 = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer c42 = new com.aigicrm.module.customer.entity.BizCustomer();
        c42.setId(42L); c42.setStatus("normal");
        when(customerMapper42.selectById(42L)).thenReturn(c42);
        com.aigicrm.common.engine.CustomerAccessService accessOk = mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(accessOk.canView(42L)).thenReturn(true);
        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, nodeMapper,
                flowMapper, customerMapper42,
                userMapper, mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                accessOk,
                mock(SysUserService.class), mock(com.aigicrm.module.customer.service.CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class), mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("extend", 42L, null, null));
        assertTrue(ex.getMessage().contains("未配置或无有效审批人"), "实际: " + ex.getMessage());
        verify(nodeMapper, never()).insert(any(com.aigicrm.module.approval.entity.BizApprovalNode.class));
    }

    // ===== 【议题二.1 裁决】管理员手动转交 =====

    @Test
    void transferNode_nonAdmin_rejected() {
        ApprovalService svc = newApprovalService(mock(BizApprovalMapper.class),
                mock(BizApprovalNodeMapper.class), mock(SeaService.class), mock(BizApprovalConfigMapper.class));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.transferNode(1L, 10L, 20L));
            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("仅平台管理员"));
        }
    }

    @Test
    void transferNode_terminalApproval_rejected() {
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        com.aigicrm.module.approval.entity.BizApproval done = approvalOf(1L, "extend", 42L);
        done.setStatus("approved");
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(done);
        ApprovalService svc = newApprovalService(approvalMapper,
                mock(BizApprovalNodeMapper.class), mock(SeaService.class), mock(BizApprovalConfigMapper.class));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.transferNode(1L, 10L, 20L));
            assertTrue(ex.getMessage().contains("已结束"));
        }
    }

    @Test
    void transferNode_valid_succeedsAndAudits() {
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectByIdForUpdate(1L)).thenReturn(approvalOf(1L, "extend", 42L));
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        com.aigicrm.module.approval.entity.BizApprovalNode n =
                node(10L, 7L, 1, "ALL", 5L, "pending");
        n.setApprovalId(1L);
        when(nodeMapper.selectById(10L)).thenReturn(n);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        com.aigicrm.module.system.entity.SysUser target = new com.aigicrm.module.system.entity.SysUser();
        target.setId(20L);
        target.setUsername("newapprover");
        target.setStatus(1);
        target.setDeleted(0);
        when(userMapper.selectById(20L)).thenReturn(target);

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), mock(BizCustomerMapper.class),
                userMapper, mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(SysUserService.class), mock(com.aigicrm.module.customer.service.CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class), mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            assertDoesNotThrow(() -> svc.transferNode(1L, 10L, 20L));
        }
        verify(nodeMapper).updateById(argThat(
                (com.aigicrm.module.approval.entity.BizApprovalNode x) ->
                        Long.valueOf(20L).equals(x.getApproverId())));
    }

    /** 【回归修复 2026-10-09 公海死循环】延期审批重算基准=max(开票日,注册日)——legacy 老票不得把
     *  到期日拖回过去(生产 303: 2015年老票+捞取日2026-07-28, COALESCE 旧口径延期即变 2017 年过期) */
    @Test
    void approve_extend_legacyInvoiceKeepsExpireAnchoredToRegister() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(21L, "extend", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(21L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(21L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        com.aigicrm.module.approval.entity.BizApprovalNode mine = node(601L, 7L, 1, null, null, "approved");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(601L, 7L, 1, null, null, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer customer = new com.aigicrm.module.customer.entity.BizCustomer();
        customer.setId(42L);
        customer.setRegisterDate(java.time.LocalDate.of(2026, 7, 28));
        customer.setLastInvoiceDate(java.time.LocalDate.of(2015, 5, 12));
        customer.setApprovedExtendDays(0);
        when(customerMapper.selectById(42L)).thenReturn(customer);
        // 【审查 backend-3】执行侧改走行锁读
        when(customerMapper.selectByIdForUpdate(42L)).thenReturn(customer);

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapper,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class), mock(SysUserService.class),
                realCustomerService(customerMapper),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        assertDoesNotThrow(() -> svc.approve(21L, "同意"));

        ArgumentCaptor<com.aigicrm.module.customer.entity.BizCustomer> cap =
                ArgumentCaptor.forClass(com.aigicrm.module.customer.entity.BizCustomer.class);
        verify(customerMapper).updateById(cap.capture());
        com.aigicrm.module.customer.entity.BizCustomer saved = cap.getValue();
        // 基准=max(2015-05-12, 2026-07-28)=注册日: expire=2026-07-28+365(保护,2026-11-02口径A)+360(本次延期)
        java.time.LocalDate expected = java.time.LocalDate.of(2026, 7, 28).plusDays(365).plusDays(360);
        assertEquals(expected, saved.getExpireDate(), "实际: " + saved.getExpireDate());
        assertEquals(360, saved.getApprovedExtendDays());
        assertTrue(saved.getExpireDate().isAfter(java.time.LocalDate.now()), "延期后到期日必须在未来");
    }

    // ===== 【L1 2026-10-12 测试报告】自审自批双闸门 =====

    /** approve 侧闸门: 申请人=当前审批人 → 拒绝(此前 extend 节点回退直属领导,领导本人发起即可自批) */
    @Test
    void approve_applicantSelf_rejectsAsSelfApproval() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        // 申请人=7 与审批人=7 同人(测试报告 L1 场景: 直属领导给自己客户发起延期)
        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(31L, "extend", 42L);
        approval.setApplicantId(7L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(31L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(31L)).thenReturn(approval);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(701L, 7L, 1, "ALL", 10L, "pending"));

        ApprovalService svc = newApprovalService(approvalMapper, nodeMapper, mock(SeaService.class), mock(BizApprovalConfigMapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(31L, "同意"));
        assertTrue(ex.getMessage().contains("自审自批"), "实际: " + ex.getMessage());
        // 节点不得被流转
        verify(nodeMapper, never()).update(isNull(), any());
    }

    /** submit 侧闸门: 节点解析结果全部是申请人本人 → 拒绝提交并回滚(不产生生而无人可审的单据) */
    @Test
    void submit_onlySelfApproverNodes_rejects() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setDataScope("all");
        setCurrentUser(u);

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService configService = mock(ApprovalConfigService.class);
        // 配置路径解析出的审批人=申请人本人(7)
        when(configService.createNodes(any(), anyString())).thenReturn(java.util.List.of(7L));

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);

        // extend 守卫需客户存在且可查看
        BizCustomerMapper customerMapperCfg = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer cCfg = new com.aigicrm.module.customer.entity.BizCustomer();
        cCfg.setId(42L); cCfg.setStatus("normal");
        when(customerMapperCfg.selectById(42L)).thenReturn(cCfg);
        com.aigicrm.common.engine.CustomerAccessService accessCfg = mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(accessCfg.canView(42L)).thenReturn(true);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(node(701L, 7L, 1, "ALL", 10L, "pending")));

        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapperCfg,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                accessCfg,
                mock(SysUserService.class), mock(com.aigicrm.module.customer.service.CustomerService.class), mock(com.aigicrm.module.customer.service.CustomerEventService.class), configService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.submit("extend", 42L, null, null));
        assertTrue(ex.getMessage().contains("自审自批"), "实际: " + ex.getMessage());
    }

    // ===== 【延期B 2026-10-17 用户裁决】天数进审批单/审批人可改 =====

    /** 提交侧:extend 快照申请天数进 extraData(默认 sys_config,显式传值优先) */
    @Test
    void submit_extend_snapshotsDaysIntoExtraData() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06"));
        setCurrentUser(u);

        com.aigicrm.module.system.service.SysConfigService configService =
                mock(com.aigicrm.module.system.service.SysConfigService.class);
        when(configService.getValue("customer.extend.days")).thenReturn("270");

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        when(configMapper.selectOne(any())).thenReturn(null);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);
        BizCustomerMapper customerMapperCfg = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer cCfg = new com.aigicrm.module.customer.entity.BizCustomer();
        cCfg.setId(42L); cCfg.setStatus("normal");
        when(customerMapperCfg.selectById(42L)).thenReturn(cCfg);
        com.aigicrm.common.engine.CustomerAccessService accessCfg = mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(accessCfg.canView(42L)).thenReturn(true);

        // 配置化流程:createNodes 解析出审批人 8(≠申请人 7),节点行经 nodeMapper.selectList 反映
        com.aigicrm.module.approval.service.ApprovalConfigService approvalConfigService =
                mock(com.aigicrm.module.approval.service.ApprovalConfigService.class);
        when(approvalConfigService.createNodes(any(), anyString())).thenReturn(java.util.List.of(8L));
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(node(801L, 8L, 1, "ALL", 10L, "pending")));

        ApprovalService svc = new ApprovalService(
                configMapper, approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapperCfg,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                accessCfg, mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                approvalConfigService, configService,
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        assertDoesNotThrow(() -> svc.submit("extend", 42L, null, null));
        ArgumentCaptor<BizApproval> cap = ArgumentCaptor.forClass(BizApproval.class);
        verify(approvalMapper).insert(cap.capture());
        assertTrue(cap.getValue().getExtraData().contains("\"extendDays\":270"),
                "未带天数的提交须快照 sys_config 值, 实际: " + cap.getValue().getExtraData());

        // 显式传 90 天:显式值优先于配置
        org.mockito.Mockito.clearInvocations(approvalMapper);
        assertDoesNotThrow(() -> svc.submit("extend", 42L, null, "{\"extendDays\":90}"));
        verify(approvalMapper).insert(cap.capture());
        assertTrue(cap.getValue().getExtraData().contains("\"extendDays\":90"),
                "显式天数须原样进单, 实际: " + cap.getValue().getExtraData());

        // 越界值拒绝
        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> svc.submit("extend", 42L, null, "{\"extendDays\":0}"));
        assertTrue(ex2.getMessage().contains("1~3650"), "实际: " + ex2.getMessage());
    }

    /** 执行侧:审批单 extraData.extendDays 优先于 sys_config(90 天生效,不再用配置 360) */
    @Test
    void approve_extend_extraDataDaysUsedOverConfig() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(31L, "extend", 42L);
        approval.setExtraData("{\"extendDays\":90}");
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(31L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(31L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        com.aigicrm.module.approval.entity.BizApprovalNode mine = node(611L, 7L, 1, null, null, "approved");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(611L, 7L, 1, null, null, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer customer = new com.aigicrm.module.customer.entity.BizCustomer();
        customer.setId(42L);
        customer.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        customer.setApprovedExtendDays(0);
        when(customerMapper.selectById(42L)).thenReturn(customer);
        when(customerMapper.selectByIdForUpdate(42L)).thenReturn(customer);

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapper,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class), mock(SysUserService.class),
                realCustomerService(customerMapper),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        assertDoesNotThrow(() -> svc.approve(31L, "同意"));

        ArgumentCaptor<com.aigicrm.module.customer.entity.BizCustomer> cap =
                ArgumentCaptor.forClass(com.aigicrm.module.customer.entity.BizCustomer.class);
        verify(customerMapper).updateById(cap.capture());
        com.aigicrm.module.customer.entity.BizCustomer saved = cap.getValue();
        assertEquals(90, saved.getApprovedExtendDays(), "执行天数须取审批单 extraData(90), 非 sys_config 默认(360)");
        java.time.LocalDate expected = java.time.LocalDate.of(2026, 1, 1).plusDays(365).plusDays(90);
        assertEquals(expected, saved.getExpireDate(), "实际: " + saved.getExpireDate());
    }

    /** 审批人调整:approve 带 extendDays=180 → 合并进 extraData(执行/留痕单源)且按 180 生效 */
    @Test
    void approve_extend_daysOverrideMergedAndExecuted() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval approval = approvalOf(32L, "extend", 42L);
        approval.setExtraData("{\"extendDays\":360}");
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(32L)).thenReturn(approval);
        when(approvalMapper.selectByIdForUpdate(32L)).thenReturn(approval);
        when(approvalMapper.update(isNull(), any())).thenReturn(1);

        com.aigicrm.module.approval.entity.BizApprovalNode mine = node(621L, 7L, 1, null, null, "approved");
        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(621L, 7L, 1, null, null, "pending"));
        when(nodeMapper.update(isNull(), any())).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(java.util.List.of(mine));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer customer = new com.aigicrm.module.customer.entity.BizCustomer();
        customer.setId(42L);
        customer.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        customer.setApprovedExtendDays(0);
        when(customerMapper.selectById(42L)).thenReturn(customer);
        when(customerMapper.selectByIdForUpdate(42L)).thenReturn(customer);

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), customerMapper,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class), mock(SysUserService.class),
                realCustomerService(customerMapper),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        assertDoesNotThrow(() -> svc.approve(32L, "同意", 180));

        assertTrue(approval.getExtraData().contains("\"extendDays\":180"),
                "调整值须合并回审批单 extraData, 实际: " + approval.getExtraData());
        verify(approvalMapper).updateById(approval);
        ArgumentCaptor<com.aigicrm.module.customer.entity.BizCustomer> cap =
                ArgumentCaptor.forClass(com.aigicrm.module.customer.entity.BizCustomer.class);
        verify(customerMapper).updateById(cap.capture());
        assertEquals(180, cap.getValue().getApprovedExtendDays(), "执行须按审批人调整后的 180 天");
    }

    /** 非 extend 单带 extendDays → 拒绝;越界天数 → 拒绝(均不落任何节点流转) */
    @Test
    void approve_extendDaysGuard_nonExtendAndOutOfRangeRejected() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        setCurrentUser(u, "approval:view");

        com.aigicrm.module.approval.entity.BizApproval claim = approvalOf(33L, "claim", 42L);
        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectById(33L)).thenReturn(claim);
        when(approvalMapper.selectByIdForUpdate(33L)).thenReturn(claim);

        BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
        when(nodeMapper.selectOne(any())).thenReturn(node(631L, 7L, 1, null, null, "pending"));

        ApprovalService svc = new ApprovalService(
                mock(BizApprovalConfigMapper.class), approvalMapper, nodeMapper,
                mock(BizApprovalFlowMapper.class), mock(BizCustomerMapper.class),
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class), mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                mock(ApprovalConfigService.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.approve(33L, "同意", 30));
        assertTrue(ex.getMessage().contains("仅延期审批"), "实际: " + ex.getMessage());
        verify(nodeMapper, never()).update(isNull(), any());

        com.aigicrm.module.approval.entity.BizApproval extend = approvalOf(34L, "extend", 42L);
        when(approvalMapper.selectById(34L)).thenReturn(extend);
        when(approvalMapper.selectByIdForUpdate(34L)).thenReturn(extend);
        BusinessException ex2 = assertThrows(BusinessException.class, () -> svc.approve(34L, "同意", 0));
        assertTrue(ex2.getMessage().contains("1~3650"), "实际: " + ex2.getMessage());
        BusinessException ex3 = assertThrows(BusinessException.class, () -> svc.approve(34L, "同意", 4000));
        assertTrue(ex3.getMessage().contains("1~3650"), "实际: " + ex3.getMessage());
        verify(nodeMapper, never()).update(isNull(), any());
    }

    // ===== 【A3 2026-10-20】审批编号 MAX+1 取号并发兜底(有限重试) =====
    // 真实并发集成测试(仅一方成功或双方不同号)需 Testcontainers/MySQL, 仓库测试栈为纯 mock 单测
    // (审计 F7, P2), 本组以 mock 钉"冲突→退让重试→超限抛原始异常"语义。

    @Test
    void submit_codeConflict_retriesWithBumpedSuffix_thenSucceeds() {
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);
        // 并发事务已抢占 max+1 号: 首次 insert 撞唯一键, 第二次(退让 max+2)成功。
        // 重试复用同一实体(编号字段被改写), 用 thenAnswer 在调用时刻快照编号。
        java.util.List<String> codesAtInsert = new java.util.ArrayList<>();
        when(approvalMapper.insert(any(BizApproval.class))).thenAnswer(inv -> {
            codesAtInsert.add(((BizApproval) inv.getArgument(0)).getApprovalCode());
            if (codesAtInsert.size() == 1) {
                throw new org.springframework.dao.DuplicateKeyException("Duplicate entry 'P2026-001'");
            }
            return 1;
        });

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        assertDoesNotThrow(() -> svc.submit("claim", 42L, null, null));
        verify(approvalMapper, times(2)).insert(any(BizApproval.class));
        String p = "P" + java.time.Year.now().getValue() + "-";
        assertEquals(java.util.List.of(p + "001", p + "002"), codesAtInsert,
                "首次按 max+1 取号; 冲突后必须退让一号(max+2), 不得重复使用被抢占的号码");
    }

    @Test
    void submit_codeConflict_exhaustsRetries_rethrowsOriginal() {
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
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenReturn(0);
        org.springframework.dao.DuplicateKeyException boom =
                new org.springframework.dao.DuplicateKeyException("持续冲突");
        when(approvalMapper.insert(any(BizApproval.class))).thenThrow(boom);

        ApprovalService svc = newService(seaPoolMapper, approvalMapper, mock(BizApprovalNodeMapper.class));
        org.springframework.dao.DuplicateKeyException ex = assertThrows(
                org.springframework.dao.DuplicateKeyException.class, () -> svc.submit("claim", 42L, null, null));
        assertEquals(boom, ex, "超限必须抛原始异常(不吞错、不改语义为业务异常)");
        verify(approvalMapper, times(3)).insert(any(BizApproval.class)); // 上限=首次+2次重试
    }

    // ===== 【A3 2026-10-21 复核补验】取号行锁 + 真实多线程并发 =====
    // 共用装配: 需要持有 approvalMapper/jdbc/configMapper/customerMapper 实例做验证。

    private ApprovalService submitFixtureService(BizApprovalMapper approvalMapper,
                                                 org.springframework.jdbc.core.JdbcTemplate jdbc,
                                                 BizApprovalConfigMapper configMapper,
                                                 ApprovalConfigService approvalConfigService,
                                                 BizCustomerMapper customerMapper,
                                                 com.aigicrm.common.engine.CustomerAccessService access) {
        return new ApprovalService(configMapper, approvalMapper, mock(BizApprovalNodeMapper.class),
                mock(BizApprovalFlowMapper.class), customerMapper,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                mock(BizSeaPoolMapper.class), mock(SeaService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class), mock(SeaConfigService.class),
                access, mock(SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                approvalConfigService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                jdbc, mock(com.aigicrm.security.SecurityAuditService.class));
    }

    /** 【A3 取号行锁】有集团上下文时: 先锁集团行(sys_group FOR UPDATE), 取号走 FOR UPDATE 锁定读 */
    @Test
    void submit_withGroupContext_locksGroupRowAndUsesForUpdateMaxRead() {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06")); // 非 admin
        u.setCurrentGroupId(5L);                 // 集团上下文 → 走取号行锁路径
        setCurrentUser(u);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        when(approvalMapper.selectMaxCodeByPrefixForUpdate(anyString(), eq(5L))).thenReturn(0);
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService approvalConfigService = mock(ApprovalConfigService.class);
        when(approvalConfigService.createNodes(any(), anyString())).thenReturn(java.util.List.of(8L));
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer normal =
                new com.aigicrm.module.customer.entity.BizCustomer();
        normal.setStatus("normal");
        when(customerMapper.selectById(any())).thenReturn(normal);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canView(any())).thenReturn(true);

        ApprovalService svc = submitFixtureService(approvalMapper, jdbc, configMapper,
                approvalConfigService, customerMapper, access);
        try {
            assertDoesNotThrow(() -> svc.submit("extend", 51L, null, null));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        verify(jdbc).queryForList(contains("sys_group"), eq(Long.class), eq(5L));
        verify(approvalMapper).selectMaxCodeByPrefixForUpdate(anyString(), eq(5L));
        verify(approvalMapper, never()).selectMaxCodeByPrefix(anyString(), any());
    }

    /**
     * 【A3 并发验证·线程级】真实多线程并发提交(3 个不同客户的延期单, 现实中同前缀取号竞争场景)
     * + 线程安全模拟唯一索引(取号读与插入判重均看最新"已提交"集合): 验收命题"仅一方成功占用
     * 每个号"——3 方必须全部成功且编号互不相同(任一 500/重试超限都会在此暴露)。
     * 非真实 DB(仓库无 Testcontainers/H2, 审计 F7)——真实数据库级并发集成测试仍 BLOCKED。
     * 无集团上下文(无锁路径)下验证; 有集团上下文时生产路径由集团行锁串行化
     * (见 submit_withGroupContext_locksGroupRowAndUsesForUpdateMaxRead)。
     */
    @Test
    void submit_concurrentThreads_simulatedUniqueIndex_allSucceedWithDistinctCodes() throws Exception {
        SecurityUser u = new SecurityUser();
        u.setUserId(7L);
        u.setRoleCodes(java.util.List.of("06")); // 非 admin 且无集团上下文 → 无锁路径, 纯退让重试
        setCurrentUser(u);

        BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
        java.util.Set<String> committedCodes =
                java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        when(approvalMapper.selectMaxCodeByPrefix(anyString(), any())).thenAnswer(inv -> {
            String pfx = inv.getArgument(0);
            return committedCodes.stream()
                    .filter(code -> code.startsWith(pfx))
                    .mapToInt(code -> Integer.parseInt(code.substring(pfx.length())))
                    .max().orElse(0);
        });
        when(approvalMapper.insert(any(BizApproval.class))).thenAnswer(inv -> {
            BizApproval a = inv.getArgument(0);
            if (!committedCodes.add(a.getApprovalCode())) {
                throw new org.springframework.dao.DuplicateKeyException("dup " + a.getApprovalCode());
            }
            return 1;
        });
        when(approvalMapper.selectCount(any())).thenReturn(0L); // 各客户均无在途延期单

        BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
        when(configMapper.selectCount(any())).thenReturn(1L);
        ApprovalConfigService approvalConfigService = mock(ApprovalConfigService.class);
        when(approvalConfigService.createNodes(any(), anyString())).thenReturn(java.util.List.of(8L));
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer normal =
                new com.aigicrm.module.customer.entity.BizCustomer();
        normal.setStatus("normal");
        when(customerMapper.selectById(any())).thenReturn(normal);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canView(any())).thenReturn(true);

        ApprovalService svc = submitFixtureService(approvalMapper,
                mock(org.springframework.jdbc.core.JdbcTemplate.class), configMapper,
                approvalConfigService, customerMapper, access);
        try {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            long[] customerIds = {51L, 52L, 53L};
            for (long cid : customerIds) {
                futures.add(pool.submit(() -> svc.submit("extend", cid, null, null)));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    throw new AssertionError("并发提交任一方失败(含重试超限)即验收不通过", e);
                }
            }
            pool.shutdown();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertEquals(3, committedCodes.size(),
                "3 方并发提交必须全部成功且编号互不相同(集合去重后恰为 3 → 仅一方占用每个号)");
    }
}
