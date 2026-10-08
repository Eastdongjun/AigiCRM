package com.aigicrm.task;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.service.SysConfigService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【N3 2026-08-08】从未开票客户应创建 invoice_never（待首次开票），
 * 而非并入 invoice_overdue 并写死 9999 天超期。
 */
@SuppressWarnings("unchecked")
class CustomerExpireTaskTest {

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        // LambdaWrapper 依赖 TableInfo 静态缓存: 本类单跑时无其他类可蹭, 自足初始化(全量跑时幂等)
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), com.aigicrm.module.customer.entity.BizCustomer.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), com.aigicrm.module.reminder.entity.BizReminder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), com.aigicrm.module.approval.entity.BizApproval.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), com.aigicrm.module.approval.entity.BizApprovalNode.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), com.aigicrm.module.sea.entity.BizSeaPool.class);
    }

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
    private final SeaService seaService = mock(SeaService.class);
    private final SysConfigService configService = mock(SysConfigService.class);
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private final com.aigicrm.module.approval.mapper.BizApprovalMapper approvalMapper = mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class);
    private final com.aigicrm.module.approval.mapper.BizApprovalNodeMapper approvalNodeMapper = mock(com.aigicrm.module.approval.mapper.BizApprovalNodeMapper.class);
    private final com.aigicrm.module.sea.mapper.BizSeaPoolMapper seaPoolMapper = mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class);
    // 【H2 2026-10-08】系统身份包装依赖:mock 默认 loadUserByUsername 返回 null → 包装降级为
    // 无身份执行,既有直调方法的无认证行为与断言不受影响
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService =
            mock(org.springframework.security.core.userdetails.UserDetailsService.class);
    private final CustomerExpireTask task = new CustomerExpireTask(customerMapper, reminderMapper, seaService, configService, meterRegistry, approvalMapper, approvalNodeMapper, seaPoolMapper, userDetailsService);

    @Test
    void neverInvoiced_createsInvoiceNever_notOverdue9999() {
        BizCustomer overdue = new BizCustomer();
        overdue.setId(1L);
        overdue.setCustomerName("已开票超期客户");
        overdue.setLastInvoiceDate(LocalDate.now().minusDays(400));
        overdue.setOwnerId(11L);
        overdue.setOfficeId(3L);

        BizCustomer never = new BizCustomer();
        never.setId(2L);
        never.setCustomerName("从未开票客户");
        never.setLastInvoiceDate(null);
        never.setOwnerId(12L);
        never.setOfficeId(4L);

        // selectList 调用顺序：1)保护期到期 2)开票超期(非空) 3)从未开票(NULL)
        when(customerMapper.selectList(any())).thenReturn(List.of(), List.of(overdue), List.of(never));
        when(reminderMapper.selectCount(any())).thenReturn(0L);

        task.checkCustomerExpire();

        verify(reminderMapper, times(2)).insert(any(BizReminder.class));
        // 已开票超期 → invoice_overdue，超期天数为实际值（400）
        verify(reminderMapper).insert(argThat((BizReminder r) ->
                "invoice_overdue".equals(r.getReminderType()) && r.getOverdueDays() == 400));
        // 从未开票 → invoice_never，overdueDays=0，不再是 9999
        verify(reminderMapper).insert(argThat((BizReminder r) ->
                "invoice_never".equals(r.getReminderType()) && r.getOverdueDays() == 0));
        verify(reminderMapper, never()).insert(argThat((BizReminder r) ->
                r.getOverdueDays() != null && r.getOverdueDays() == 9999));
    }

    @Test
    void existingPendingReminder_skipsDuplicate() {
        BizCustomer never = new BizCustomer();
        never.setId(2L);
        never.setCustomerName("从未开票客户");
        never.setLastInvoiceDate(null);

        when(customerMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(never));
        when(reminderMapper.selectCount(any())).thenReturn(1L); // 已有待处理提醒

        task.checkCustomerExpire();

        verify(reminderMapper, never()).insert(any(BizReminder.class));
    }

    /**
     * 【裁决 2026-09-24 GPT清单P1-018 带默认值】从未开票提醒建客宽限期(invoice.never.grace.days, 默认30, 0=无宽限):
     * mapper 为 mock, SQL 过滤不会真执行, 故除行为断言外还捕获第 3 次 selectList(从未开票查询)的 wrapper,
     * 断言 created_at < (今天-30)0点 条件确实下推——否则宽限内用例缺少代码改动也会绿。
     */
    private com.baomidou.mybatisplus.core.conditions.AbstractWrapper<BizCustomer, ?, ?> captureNeverQuery() {
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizCustomer>> cap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        // selectList 调用顺序: 1)保护期 2)开票超期 3)从未开票
        verify(customerMapper, times(3)).selectList(cap.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<BizCustomer, ?, ?> neverQuery =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<BizCustomer, ?, ?>) cap.getAllValues().get(2);
        org.junit.jupiter.api.Assertions.assertTrue(
                neverQuery.getTargetSql().contains("created_at"),
                "从未开票查询应含 created_at 宽限条件, 实际: " + neverQuery.getTargetSql());
        java.time.LocalDateTime expectedCutoff = LocalDate.now().minusDays(30).atStartOfDay();
        org.junit.jupiter.api.Assertions.assertTrue(
                neverQuery.getParamNameValuePairs().containsValue(expectedCutoff),
                "宽限阈值应为(今天-30)0点(默认30, 未单独配配置键), 实际参数: " + neverQuery.getParamNameValuePairs());
        return neverQuery;
    }

    @Test
    void neverInvoiced_withinGraceDays_noReminder() {
        BizCustomer fresh = new BizCustomer();
        fresh.setId(21L);
        fresh.setCustomerName("建客10天客户(宽限内)");
        fresh.setLastInvoiceDate(null);
        fresh.setOwnerId(12L);
        fresh.setOfficeId(4L);
        fresh.setCreatedAt(java.time.LocalDateTime.now().minusDays(10));

        // selectList 调用顺序：1)保护期到期 2)开票超期(非空) 3)从未开票(NULL+created_at宽限过滤, 建客10天<宽限30天被排除)
        when(customerMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of());
        when(reminderMapper.selectCount(any())).thenReturn(0L);

        task.checkCustomerExpire();

        captureNeverQuery();
        verify(reminderMapper, never()).insert(any(BizReminder.class));
    }

    @Test
    void neverInvoiced_beyondGraceDays_createsReminder() {
        BizCustomer aged = new BizCustomer();
        aged.setId(22L);
        aged.setCustomerName("建客40天客户(宽限外)");
        aged.setLastInvoiceDate(null);
        aged.setOwnerId(12L);
        aged.setOfficeId(4L);
        aged.setCreatedAt(java.time.LocalDateTime.now().minusDays(40));

        // 建客40天 > 宽限30天, 未被 created_at 条件排除, 照常建 invoice_never 提醒
        when(customerMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(aged));
        when(reminderMapper.selectCount(any())).thenReturn(0L);

        task.checkCustomerExpire();

        captureNeverQuery();
        verify(reminderMapper).insert(argThat((BizReminder r) ->
                "invoice_never".equals(r.getReminderType()) && r.getOverdueDays() == 0));
    }

    /**
     * 【裁决 2026-09-24】公海捞取审批锁超时自动释放: 锁定超过 sea.claim.lock.days(默认3)的池行
     * 释放回 available 并同步拒绝 reviewing 态 claim 审批及其 pending 节点; 0=不启用。
     */
    @Test
    void staleClaimLock_releasedWithApprovalRejected() {
        com.aigicrm.module.sea.entity.BizSeaPool stale = new com.aigicrm.module.sea.entity.BizSeaPool();
        stale.setId(31L);
        stale.setCustomerId(77L);
        stale.setStatus("pending_approval");
        stale.setClaimDate(java.time.LocalDateTime.now().minusDays(5));
        when(seaPoolMapper.selectList(any())).thenReturn(java.util.List.of(stale));
        when(configService.getValue("sea.claim.lock.days")).thenReturn("3");
        com.aigicrm.module.approval.entity.BizApproval claim = new com.aigicrm.module.approval.entity.BizApproval();
        claim.setId(901L);
        when(approvalMapper.selectOne(any())).thenReturn(claim);

        task.releaseStaleClaimLocks();

        var poolCap = org.mockito.ArgumentCaptor.forClass(com.aigicrm.module.sea.entity.BizSeaPool.class);
        verify(seaPoolMapper).updateById(poolCap.capture());
        org.junit.jupiter.api.Assertions.assertEquals("available", poolCap.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertNull(poolCap.getValue().getClaimUserId());
        verify(approvalMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(approvalNodeMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void claimLockRelease_disabledWhenConfigZero() {
        when(configService.getValue("sea.claim.lock.days")).thenReturn("0");
        task.releaseStaleClaimLocks();
        verify(seaPoolMapper, never()).selectList(any());
    }

    /**
     * 【H2 2026-10-08】2AM 任务链系统身份包装:任务体内以 admin(id=1) 认证运行(租户拦截器
     * 对 admin 放行=跨集团,prod 下查询不再被改写为恒空),结束后还原为进入前的认证(通常 null)。
     */
    @Test
    void systemIdentityWrapper_setsAdminDuringRunAndRestoresAfter() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.aigicrm.security.SecurityUser admin = new com.aigicrm.security.SecurityUser();
        admin.setUserId(1L);
        admin.setUsername("admin");
        admin.setRoleCodes(java.util.List.of("admin"));
        when(userDetailsService.loadUserByUsername("1")).thenReturn(admin);

        java.util.concurrent.atomic.AtomicReference<org.springframework.security.core.Authentication> duringRun =
                new java.util.concurrent.atomic.AtomicReference<>();
        task.runWithSystemIdentity(() -> duringRun.set(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()));

        org.junit.jupiter.api.Assertions.assertNotNull(duringRun.get(), "任务体内必须有系统身份认证");
        org.junit.jupiter.api.Assertions.assertTrue(duringRun.get().isAuthenticated());
        org.junit.jupiter.api.Assertions.assertSame(admin, duringRun.get().getPrincipal(),
                "系统身份必须是 id=1 的 admin 账号");
        org.junit.jupiter.api.Assertions.assertNull(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "任务结束后必须还原为进入前的认证(此处为 null),线程复用不得泄漏系统身份");
    }

    /** 【H2】admin 加载失败(库异常/admin 被禁用)时降级为无身份执行,不抛错不阻断任务 */
    @Test
    void systemIdentityWrapper_toleratesAdminLoadFailure() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        when(userDetailsService.loadUserByUsername("1"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("admin"));

        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        task.runWithSystemIdentity(() -> ran.set(true));

        org.junit.jupiter.api.Assertions.assertTrue(ran.get(), "加载失败必须降级执行任务体,不得阻断");
        org.junit.jupiter.api.Assertions.assertNull(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "降级路径不得污染 SecurityContext");
    }
}
