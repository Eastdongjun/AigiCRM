package com.ycmn.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.reminder.entity.BizReminder;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.service.SysConfigService;
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
                    seaService.addToSea(customer.getId(), "保护期满");
                    log.info("客户 {} 保护期已到，进入公海", customer.getCustomerName());
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
            try { return Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private void checkInvoiceOverdue(LocalDate today) {
        int overdueDaysConfig = getConfigInt("invoice.overdue.days", 365);
        LocalDate oneYearAgo = today.minusDays(overdueDaysConfig);
        List<BizCustomer> overdueCustomers = customerMapper.selectList(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0)
                        .ne(BizCustomer::getStatus, "sea")
                        .and(w -> w.lt(BizCustomer::getLastInvoiceDate, oneYearAgo)
                                .or().isNull(BizCustomer::getLastInvoiceDate))
        );

        for (BizCustomer customer : overdueCustomers) {
            // 检查是否已有未处理的提醒
            long existCount = reminderMapper.selectCount(
                    new LambdaQueryWrapper<BizReminder>()
                            .eq(BizReminder::getCustomerId, customer.getId())
                            .eq(BizReminder::getReminderType, "invoice_overdue")
                            .eq(BizReminder::getStatus, "pending")
            );
            if (existCount == 0) {
                long overdueDays = customer.getLastInvoiceDate() != null
                        ? ChronoUnit.DAYS.between(customer.getLastInvoiceDate(), today)
                        : 9999;
                BizReminder reminder = new BizReminder();
                reminder.setCustomerId(customer.getId());
                reminder.setReminderType("invoice_overdue");
                reminder.setOwnerId(customer.getOwnerId());
                reminder.setOfficeId(customer.getOfficeId());
                reminder.setOverdueDays((int) overdueDays);
                reminder.setStatus("pending");
                reminderMapper.insert(reminder);
            }
        }
    }
}
