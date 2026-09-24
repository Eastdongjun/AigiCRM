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
    private final CustomerExpireTask task = new CustomerExpireTask(customerMapper, reminderMapper, seaService, configService, meterRegistry, approvalMapper, approvalNodeMapper, seaPoolMapper);

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
}
