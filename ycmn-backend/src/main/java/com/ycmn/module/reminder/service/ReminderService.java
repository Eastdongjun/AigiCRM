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
    private final com.ycmn.common.scope.OrgScopeResolver scopeResolver;

    /**
     * 【修复 提醒模块越权】此前 page()/expiringPage()/pendingCount() 的 ownerId 仅在调用方
     * 显式传参时才生效，不传 ownerId 即返回全公司提醒（含客户开票日期等信息）——任何持有
     * reminder:view 的角色都能看到他人数据。统一改为按 OrgScopeResolver 六级数据范围过滤，
     * all 范围不过滤；其余范围恒含"本人"；显式指定 ownerId 时需落在可见范围内，否则返回空集
     * （防手改参数绕过，同客户模块 §6.3 双重保险原则）。
     */
    private void applyOwnerScope(LambdaQueryWrapper<BizReminder> wrapper, Long ownerId) {
        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible == null) { // all：不过滤
            if (ownerId != null) wrapper.eq(BizReminder::getOwnerId, ownerId);
            return;
        }
        if (ownerId != null) {
            if (visible.contains(ownerId)) {
                wrapper.eq(BizReminder::getOwnerId, ownerId);
            } else {
                wrapper.eq(BizReminder::getOwnerId, -1L); // 越权请求：返回空集
            }
        } else {
            wrapper.in(BizReminder::getOwnerId, visible.isEmpty() ? List.of(-1L) : visible);
        }
    }

    public IPage<BizReminder> page(int current, int size, String type, String keyword,
                                    Long officeId, Long ownerId, String status, Long deptId) {
        LambdaQueryWrapper<BizReminder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(type)) {
            wrapper.eq(BizReminder::getReminderType, type);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(BizReminder::getStatus, status);
        }
        if (officeId != null) {
            wrapper.eq(BizReminder::getOfficeId, officeId);
        }
        if (deptId != null) {
            wrapper.in(BizReminder::getOfficeId, com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        }
        applyOwnerScope(wrapper, ownerId);
        wrapper.orderByDesc(BizReminder::getCreatedAt);

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
                                                     Long officeId, Long ownerId, Long deptId) {
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
        if (deptId != null) {
            wrapper.in(BizCustomer::getOfficeId, com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        } else if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }

        // 按数据范围过滤负责人（同 page() 修复，替换原"非管理员只看自己"的粗粒度判断）
        List<Long> visibleOwners = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visibleOwners == null) { // all：不过滤
            if (ownerId != null) wrapper.eq(BizCustomer::getOwnerId, ownerId);
        } else if (ownerId != null) {
            if (visibleOwners.contains(ownerId)) {
                wrapper.eq(BizCustomer::getOwnerId, ownerId);
            } else {
                wrapper.eq(BizCustomer::getOwnerId, -1L); // 越权请求：返回空集
            }
        } else {
            wrapper.in(BizCustomer::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
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

        // 按数据范围统计（同 page()/expiringPage() 修复；null=all 不过滤）
        List<Long> visibleOwners = scopeResolver.resolve(SecurityUtils.getCurrentUser());

        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<BizCustomer>()
                .isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .ne(BizCustomer::getStatus, "sea");
        if (visibleOwners != null) {
            custWrapper.in(BizCustomer::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
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
        if (visibleOwners != null) {
            remWrapper.in(BizReminder::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
        }
        Long processedToday = reminderMapper.selectCount(remWrapper);

        LambdaQueryWrapper<BizReminder> shareWrapper = new LambdaQueryWrapper<BizReminder>()
                .in(BizReminder::getReminderType, List.of("share_to", "share_by", "share_owner"))
                .eq(BizReminder::getStatus, "pending");
        if (visibleOwners != null) {
            shareWrapper.in(BizReminder::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
        }
        long sharePending = reminderMapper.selectCount(shareWrapper);

        return Math.max(0, totalExpiring - processedToday + sharePending);
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
