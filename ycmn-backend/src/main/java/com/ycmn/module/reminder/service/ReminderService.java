package com.ycmn.module.reminder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.reminder.entity.BizReminder;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final BizReminderMapper reminderMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysConfigService configService;

    public IPage<BizReminder> page(int current, int size, String type, String keyword,
                                    Long officeId, Long ownerId) {
        LambdaQueryWrapper<BizReminder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(BizReminder::getReminderType, type);
        }
        if (officeId != null) {
            wrapper.eq(BizReminder::getOfficeId, officeId);
        }
        if (ownerId != null) {
            wrapper.eq(BizReminder::getOwnerId, ownerId);
        }
        wrapper.orderByDesc(BizReminder::getOverdueDays);

        IPage<BizReminder> page = reminderMapper.selectPage(new Page<>(current, size), wrapper);
        for (BizReminder r : page.getRecords()) {
            if (r.getCustomerId() != null) {
                BizCustomer customer = customerMapper.selectById(r.getCustomerId());
                if (customer != null) {
                    r.setCustomerName(customer.getCustomerName());
                    r.setLastInvoiceDate(customer.getLastInvoiceDate());
                }
            }
            if (r.getOwnerId() != null) {
                SysUser user = userMapper.selectById(r.getOwnerId());
                if (user != null) r.setOwnerName(user.getRealName());
            }
            if (r.getOfficeId() != null) {
                SysDept dept = deptMapper.selectById(r.getOfficeId());
                if (dept != null) r.setOfficeName(dept.getDeptName());
            }
        }
        return page;
    }

    public IPage<Map<String, Object>> expiringPage(int current, int size, String keyword,
                                                     Long officeId, Long ownerId) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        int warningDays = 30;
        String val = configService.getValue("customer.warning.days");
        if (val != null) { try { warningDays = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }
        LocalDate deadline = LocalDate.now().plusDays(warningDays);
        wrapper.isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .ne(BizCustomer::getStatus, "sea")
                .orderByAsc(BizCustomer::getExpireDate);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(BizCustomer::getCustomerName, keyword);
        }
        if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }
        if (ownerId != null) {
            wrapper.eq(BizCustomer::getOwnerId, ownerId);
        }

        // 非管理员只看自己负责的客户
        String roleCode = SecurityUtils.getCurrentRoleCode();
        if (!"admin".equals(roleCode)) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            if (currentUserId != null) {
                wrapper.eq(BizCustomer::getOwnerId, currentUserId);
            }
        }

        IPage<BizCustomer> customerPage = customerMapper.selectPage(new Page<>(current, size), wrapper);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> records = customerPage.getRecords().stream().map(c -> {
            Map<String, Object> row = new HashMap<>();
            row.put("customerId", c.getId());
            row.put("customerName", c.getCustomerName());
            row.put("expireDate", c.getExpireDate());
            row.put("remainingDays", ChronoUnit.DAYS.between(today, c.getExpireDate()));

            if (c.getOfficeId() != null) {
                SysDept dept = deptMapper.selectById(c.getOfficeId());
                if (dept != null) row.put("officeName", dept.getDeptName());
            }
            if (c.getOwnerId() != null) {
                SysUser user = userMapper.selectById(c.getOwnerId());
                if (user != null) row.put("ownerName", user.getRealName());
            }

            // 查今日跟进状态
            LocalDateTime todayStart = today.atStartOfDay();
            LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
            BizReminder todayReminder = reminderMapper.selectOne(
                    new LambdaQueryWrapper<BizReminder>()
                            .eq(BizReminder::getCustomerId, c.getId())
                            .eq(BizReminder::getReminderType, "expiring")
                            .ge(BizReminder::getCreatedAt, todayStart)
                            .lt(BizReminder::getCreatedAt, todayEnd)
                            .last("LIMIT 1")
            );
            if (todayReminder == null) {
                // 自动创建今日提醒记录
                todayReminder = new BizReminder();
                todayReminder.setCustomerId(c.getId());
                todayReminder.setReminderType("expiring");
                todayReminder.setOwnerId(c.getOwnerId());
                todayReminder.setOfficeId(c.getOfficeId());
                todayReminder.setOverdueDays((int) ChronoUnit.DAYS.between(today, c.getExpireDate()));
                todayReminder.setStatus("pending");
                reminderMapper.insert(todayReminder);
            }
            row.put("reminderId", todayReminder.getId());
            row.put("todayFollowedUp", "processed".equals(todayReminder.getStatus()));

            return row;
        }).collect(Collectors.toList());

        IPage<Map<String, Object>> result = new Page<>(current, size, customerPage.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 今日未跟进的到期提醒客户数（用于侧边栏角标）
     */
    public long pendingCount() {
        int warningDays = 30;
        String val = configService.getValue("customer.warning.days");
        if (val != null) { try { warningDays = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {} }
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(warningDays);

        // 非管理员只统计自己负责的客户
        String roleCode = SecurityUtils.getCurrentRoleCode();
        Long currentUserId = SecurityUtils.getCurrentUserId();

        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<BizCustomer>()
                .isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .ne(BizCustomer::getStatus, "sea");
        if (!"admin".equals(roleCode) && currentUserId != null) {
            custWrapper.eq(BizCustomer::getOwnerId, currentUserId);
        }
        Long totalExpiring = customerMapper.selectCount(custWrapper);

        // 查今日已跟进的客户数
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        LambdaQueryWrapper<BizReminder> remWrapper = new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getReminderType, "expiring")
                .eq(BizReminder::getStatus, "processed")
                .ge(BizReminder::getCreatedAt, todayStart)
                .lt(BizReminder::getCreatedAt, todayEnd);
        if (!"admin".equals(roleCode) && currentUserId != null) {
            remWrapper.eq(BizReminder::getOwnerId, currentUserId);
        }
        Long processedToday = reminderMapper.selectCount(remWrapper);

        return Math.max(0, totalExpiring - processedToday);
    }

    public void process(Long id) {
        BizReminder reminder = reminderMapper.selectById(id);
        if (reminder != null) {
            reminder.setStatus("processed");
            reminder.setProcessedAt(LocalDateTime.now());
            reminderMapper.updateById(reminder);
        }
    }
}
