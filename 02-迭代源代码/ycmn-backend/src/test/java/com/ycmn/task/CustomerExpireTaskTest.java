package com.ycmn.task;

import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.reminder.entity.BizReminder;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.service.SysConfigService;
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

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
    private final SeaService seaService = mock(SeaService.class);
    private final SysConfigService configService = mock(SysConfigService.class);
    private final CustomerExpireTask task = new CustomerExpireTask(customerMapper, reminderMapper, seaService, configService);

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
}
