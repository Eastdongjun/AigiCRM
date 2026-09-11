package com.aigicrm.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerExpireTask {

    private final BizCustomerMapper customerMapper;
    private final BizReminderMapper reminderMapper;
    private final SeaService seaService;
    private final SysConfigService configService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkCustomerExpire() {
        log.info("开始检查客户保护期...");
        LocalDate today = LocalDate.now();
        int warningDays = getConfigInt("customer.warning.days", 30);
        LocalDate warningDate = today.plusDays(warningDays);

        List<BizCustomer> customers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .ne(BizCustomer::getStatus, "sea")
                        .isNotNull(BizCustomer::getExpireDate)
        );

        for (BizCustomer customer : customers) {
            LocalDate expireDate = customer.getExpireDate();

            if (expireDate.isBefore(today) || expireDate.isEqual(today)) {
                // 已到期 -> 更新状态 + 进入公海
                if (!"expired".equals(customer.getProtectionStatus())) {
                    customer.setProtectionStatus("expired");
                    customer.setStatus("expired");
                    customerMapper.updateById(customer);
                }
                // 确保进了公海（防止状态已更新但未入池的情况）
                if (!"sea".equals(customer.getStatus())) {
                    // 【安全修复 2026-08-09】无公海池配置的办事处不自动入池（与手动释放一致），
                    // 避免产生 pool_id=NULL 的孤儿公海记录（普通用户不可见、也捞不了，变死数据）
                    if (seaService.matchSeaConfig(customer.getOfficeId()) != null) {
                        seaService.addToSea(customer.getId(), "保护期满");
                        log.info("客户 {} 保护期已到，进入公海", customer.getCustomerName());
                    } else {
                        log.info("客户 {} 所在办事处未配置公海池，保持过期状态", customer.getCustomerName());
                    }
                }
            } else if (expireDate.isBefore(warningDate)) {
                // 即将到期（30天内）
                if (!"expiring".equals(customer.getProtectionStatus())) {
                    customer.setProtectionStatus("expiring");
                    customer.setStatus("warning");
                    customerMapper.updateById(customer);
                }
                // 创建即将到期提醒（去重）
                long existExpiring = reminderMapper.selectCount(
                        new LambdaQueryWrapper<BizReminder>()
                                .eq(BizReminder::getCustomerId, customer.getId())
                                .eq(BizReminder::getReminderType, "expiring")
                                .eq(BizReminder::getStatus, "pending")
                );
                if (existExpiring == 0) {
                    long daysLeft = ChronoUnit.DAYS.between(today, expireDate);
                    BizReminder reminder = new BizReminder();
                    reminder.setCustomerId(customer.getId());
                    reminder.setReminderType("expiring");
                    reminder.setOwnerId(customer.getOwnerId());
                    reminder.setOfficeId(customer.getOfficeId());
                    reminder.setOverdueDays((int) daysLeft);
                    reminder.setStatus("pending");
                    reminderMapper.insert(reminder);
                    log.info("客户 {} 还有{}天到期，已创建提醒", customer.getCustomerName(), daysLeft);
                }
            }
        }

        // 检查开票超一年的客户
        checkInvoiceOverdue(today);
        log.info("客户保护期检查完成");
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 【2026-08-08 设计规则：从未开票与开票超期分开处理】
     * - 已开票但超过 invoice.overdue.days 未再开票 → invoice_overdue（超期提醒，实际超期天数）
     * - 从未开票（last_invoice_date 为 NULL）→ invoice_never（待首次开票，独立类型，不进超期告警）
     * 修复：此前 NULL 被并入超期查询并写死 9999 天，误报"超期 9999 天"。
     */
    private void checkInvoiceOverdue(LocalDate today) {
        int overdueDaysConfig = getConfigInt("invoice.overdue.days", 365);
        LocalDate oneYearAgo = today.minusDays(overdueDaysConfig);

        List<BizCustomer> overdueCustomers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .ne(BizCustomer::getStatus, "sea")
                        .isNotNull(BizCustomer::getLastInvoiceDate)
                        .lt(BizCustomer::getLastInvoiceDate, oneYearAgo)
        );
        for (BizCustomer customer : overdueCustomers) {
            int overdueDays = (int) ChronoUnit.DAYS.between(customer.getLastInvoiceDate(), today);
            createInvoiceReminder(customer, "invoice_overdue", overdueDays);
        }

        List<BizCustomer> neverInvoiced = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .ne(BizCustomer::getStatus, "sea")
                        .isNull(BizCustomer::getLastInvoiceDate)
        );
        for (BizCustomer customer : neverInvoiced) {
            createInvoiceReminder(customer, "invoice_never", 0);
        }
    }

    private void createInvoiceReminder(BizCustomer customer, String type, int overdueDays) {
        // 检查是否已有未处理的提醒（按类型去重）
        long existCount = reminderMapper.selectCount(
                new LambdaQueryWrapper<BizReminder>()
                        .eq(BizReminder::getCustomerId, customer.getId())
                        .eq(BizReminder::getReminderType, type)
                        .eq(BizReminder::getStatus, "pending")
        );
        if (existCount > 0) {
            return;
        }
        BizReminder reminder = new BizReminder();
        reminder.setCustomerId(customer.getId());
        reminder.setReminderType(type);
        reminder.setOwnerId(customer.getOwnerId());
        reminder.setOfficeId(customer.getOfficeId());
        reminder.setOverdueDays(overdueDays);
        reminder.setStatus("pending");
        reminderMapper.insert(reminder);
        log.info("客户 {} 创建提醒 type={} overdueDays={}", customer.getCustomerName(), type, overdueDays);
    }
}
