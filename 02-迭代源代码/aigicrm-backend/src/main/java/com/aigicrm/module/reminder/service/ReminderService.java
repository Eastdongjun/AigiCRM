package com.aigicrm.module.reminder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderService {
    /** 提醒提前默认天数:配置 customer.warning.days 缺失/脏值时回退该默认 */
    private static final int DEFAULT_WARNING_DAYS = 30;


    private final BizReminderMapper reminderMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysConfigService configService;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;

    /**
     * 【修复 提醒模块越权】此前 page()/expiringPage()/pendingCount() 的 ownerId 仅在调用方
     * 显式传参时才生效，不传 ownerId 即返回全公司提醒（含客户开票日期等信息）——任何持有
     * reminder:view 的角色都能看到他人数据。统一改为按 OrgScopeResolver 六级数据范围过滤，
     * all 范围不过滤；其余范围恒含"本人"；显式指定 ownerId 时需落在可见范围内，否则返回空集
     * （防手改参数绕过，同客户模块 §6.3 双重保险原则）。
     */
    private void applyOwnerScope(LambdaQueryWrapper<BizReminder> wrapper, Long ownerId) {
        // 【2026-09-19 接入权限矩阵】(reminder,view) 显式配置优先，未配置回退角色数据范围
        List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder");
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
            wrapper.in(BizReminder::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
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
        int warningDays = DEFAULT_WARNING_DAYS;
        String val = configService.getValue("customer.warning.days");
        if (val != null) { try { warningDays = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {
            // 配置脏值回退默认天数:解析失败不阻断查询(下同)
            } }
        LocalDate deadline = LocalDate.now().plusDays(warningDays);
        wrapper.isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .orderByAsc(BizCustomer::getExpireDate);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(BizCustomer::getCustomerName, keyword);
        }
        if (deptId != null) {
            wrapper.in(BizCustomer::getOfficeId, com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId));
        } else if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }

        // 按数据范围过滤负责人（同 page() 修复，替换原"非管理员只看自己"的粗粒度判断；
        // 2026-09-19 接入权限矩阵：(reminder,view) 显式配置优先）
        List<Long> visibleOwners = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder");
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

            // 跟进状态(【2026-09-23 去重口径统一】随 ensureExpiring 改为"pending 存在即不新建"后,
            // 今日行可能不存在:reminderId 指向当前 pending(无则今日已处理行),todayFollowedUp=今日处理过)
            LocalDateTime todayStart = today.atStartOfDay();
            BizReminder currentPending = reminderMapper.selectOne(
                    new LambdaQueryWrapper<BizReminder>()
                            .eq(BizReminder::getCustomerId, c.getId())
                            .eq(BizReminder::getReminderType, "expiring")
                            .eq(BizReminder::getStatus, "pending")
                            .orderByDesc(BizReminder::getId)
                            .last("LIMIT 1")
            );
            BizReminder todayProcessed = reminderMapper.selectOne(
                    new LambdaQueryWrapper<BizReminder>()
                            .eq(BizReminder::getCustomerId, c.getId())
                            .eq(BizReminder::getReminderType, "expiring")
                            .eq(BizReminder::getStatus, "processed")
                            .ge(BizReminder::getProcessedAt, todayStart)
                            .orderByDesc(BizReminder::getId)
                            .last("LIMIT 1")
            );
            boolean followedToday = todayProcessed != null;
            BizReminder todayReminder = currentPending != null ? currentPending : todayProcessed;
            // S8: GET 只读，不再自动建提醒——今日提醒物化由 POST /ensure-expiring 显式触发
            row.put("reminderId", todayReminder == null ? null : todayReminder.getId());
            row.put("todayFollowedUp", followedToday);

            return row;
        }).collect(Collectors.toList());

        IPage<Map<String, Object>> result = new Page<>(current, size, customerPage.getTotal());
        result.setRecords(records);
        return result;
    }

    /** S8: 物化今日到期的 expiring 提醒（幂等，POST 显式触发，GET 分页不再写库） */
    @org.springframework.transaction.annotation.Transactional
    public void ensureExpiring() {
        int warningDays = DEFAULT_WARNING_DAYS;
        String val = configService.getValue("customer.warning.days");
        if (val != null) { try { warningDays = Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {
                        // 配置脏值回退默认天数(同上)
                    } }
        LocalDate deadline = LocalDate.now().plusDays(warningDays);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .notIn(BizCustomer::getStatus, "sea", "archived"); // B5 统一口径: 排sea+archived
        List<Long> visibleOwners = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder");
        if (visibleOwners != null) {
            wrapper.in(BizCustomer::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
        }

        for (BizCustomer c : customerMapper.selectList(wrapper)) {
            // 【2026-09-23 去重口径统一】原判据="今日没建过就建"(createdAt 当日窗口),与凌晨任务
            // CustomerExpireTask 的"存在 pending 就不建"互相打架:页面每次触发都新造一条 pending,
            // 旧的永不清 → 生产积压 401 条 pending。统一为:存在 pending 或今日已处理 → 不建
            // (处理完当天不重复建,次日由本方法或凌晨任务再物化一条新的跟进待办)。
            Long pending = reminderMapper.selectCount(new LambdaQueryWrapper<BizReminder>()
                    .eq(BizReminder::getCustomerId, c.getId())
                    .eq(BizReminder::getReminderType, "expiring")
                    .eq(BizReminder::getStatus, "pending"));
            Long processedToday = pending != null && pending > 0 ? 1L : reminderMapper.selectCount(
                    new LambdaQueryWrapper<BizReminder>()
                            .eq(BizReminder::getCustomerId, c.getId())
                            .eq(BizReminder::getReminderType, "expiring")
                            .eq(BizReminder::getStatus, "processed")
                            .ge(BizReminder::getProcessedAt, todayStart));
            if ((pending == null || pending == 0) && (processedToday == null || processedToday == 0)) {
                BizReminder r = new BizReminder();
                r.setCustomerId(c.getId());
                r.setReminderType("expiring");
                r.setOwnerId(c.getOwnerId());
                r.setOfficeId(c.getOfficeId());
                r.setOverdueDays((int) ChronoUnit.DAYS.between(today, c.getExpireDate()));
                r.setStatus("pending");
                reminderMapper.insert(r);
            }
        }
    }

    /**
     * 今日未跟进的到期提醒客户数（用于侧边栏角标）
     */
    public long pendingCount() {
        int warningDays = DEFAULT_WARNING_DAYS;
        String val = configService.getValue("customer.warning.days");
        if (val != null) {
            try {
                warningDays = Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认天数(注明吞因,消除 S1166)
            }
        }
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(warningDays);

        // 按数据范围统计（同 page()/expiringPage() 修复；null=all 不过滤）
        List<Long> visibleOwners = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder");

        LambdaQueryWrapper<BizCustomer> custWrapper = new LambdaQueryWrapper<BizCustomer>()
                .isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .notIn(BizCustomer::getStatus, "sea", "archived"); // B5 统一口径: 排sea+archived
        if (visibleOwners != null) {
            custWrapper.in(BizCustomer::getOwnerId, visibleOwners.isEmpty() ? List.of(-1L) : visibleOwners);
        }
        Long totalExpiring = customerMapper.selectCount(custWrapper);

        // 查今日已跟进的客户数(【2026-09-23】口径随去重统一改 processedAt:今日处理过=今日跟进)
        LocalDateTime todayStart = today.atStartOfDay();
        LambdaQueryWrapper<BizReminder> remWrapper = new LambdaQueryWrapper<BizReminder>()
                .eq(BizReminder::getReminderType, "expiring")
                .eq(BizReminder::getStatus, "processed")
                .ge(BizReminder::getProcessedAt, todayStart);
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
        if (reminder == null) return;
        assertVisible(reminder, "无权处理该提醒");
        reminder.setStatus("processed");
        reminder.setProcessedAt(LocalDateTime.now());
        reminderMapper.updateById(reminder);
    }

    /**
     * 提醒详情：提醒本体 + 关联客户/负责人/部门（提醒管理列表行点击进入的详情页数据源）。
     * 【越权读防护，同 process()】非 admin 仅可读自己数据范围内的提醒，防按 ID 猜读他人提醒
     * （客户名称/开票日期属敏感信息）
     */
    public Map<String, Object> detail(Long id) {
        BizReminder reminder = reminderMapper.selectById(id);
        if (reminder == null) {
            throw new BusinessException("提醒不存在");
        }
        assertVisible(reminder, "无权查看该提醒");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", reminder.getId());
        detail.put("reminderType", reminder.getReminderType());
        detail.put("status", reminder.getStatus());
        detail.put("overdueDays", reminder.getOverdueDays());
        detail.put("createdAt", reminder.getCreatedAt());
        detail.put("updatedAt", reminder.getUpdatedAt());
        detail.put("processedAt", reminder.getProcessedAt());
        detail.put("customerId", reminder.getCustomerId());
        detail.put("ownerId", reminder.getOwnerId());
        detail.put("officeId", reminder.getOfficeId());

        BizCustomer customer = reminder.getCustomerId() == null ? null
                : customerMapper.selectById(reminder.getCustomerId());
        if (customer != null) {
            detail.put("customer", customerBrief(customer));
        }
        // 负责人/部门优先取提醒上的归属，缺省回落客户归属（客户侧字段为 @TableField(exist=false) 转瞬字段，需查表补齐）
        Long ownerId = reminder.getOwnerId() != null ? reminder.getOwnerId()
                : (customer == null ? null : customer.getOwnerId());
        if (ownerId != null) {
            SysUser user = userMapper.selectById(ownerId);
            if (user != null) detail.put("ownerName", user.getRealName());
        }
        Long officeId = reminder.getOfficeId() != null ? reminder.getOfficeId()
                : (customer == null ? null : customer.getOfficeId());
        if (officeId != null) {
            SysDept dept = deptMapper.selectById(officeId);
            if (dept != null) detail.put("officeName", dept.getDeptName());
        }
        return detail;
    }

    /** 详情页客户摘要：只暴露提醒场景需要的字段，不整表返回 */
    private Map<String, Object> customerBrief(BizCustomer c) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", c.getId());
        brief.put("customerCode", c.getCustomerCode());
        brief.put("customerName", c.getCustomerName());
        brief.put("status", c.getStatus());
        brief.put("industry", c.getIndustry());
        brief.put("region", regionOf(c));
        brief.put("expireDate", c.getExpireDate());
        brief.put("lastInvoiceDate", c.getLastInvoiceDate());
        brief.put("ownerId", c.getOwnerId());
        return brief;
    }

    private String regionOf(BizCustomer c) {
        StringBuilder region = new StringBuilder();
        for (String part : new String[]{c.getProvince(), c.getCity(), c.getDistrict()}) {
            if (StringUtils.hasText(part)) region.append(part);
        }
        return region.length() == 0 ? null : region.toString();
    }

    /** 提醒可见性校验（读/写共用）：非 admin 时负责人必须落在数据范围内，防按 ID 越权 */
    private void assertVisible(BizReminder reminder, String message) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder");
        if (visible != null && (reminder.getOwnerId() == null || !visible.contains(reminder.getOwnerId()))) {
            throw new BusinessException(message);
        }
    }
}
