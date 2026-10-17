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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReminderService {
    /** 提醒提前默认天数:配置 customer.warning.days 缺失/脏值时回退该默认 */
    private static final int DEFAULT_WARNING_DAYS = 30;

    /**
     * 【容量评估修复§2 只读并发池】JDK21 虚拟线程,专供本服务只读查询段并发调度。
     * 动机: 容量评估第一瓶颈——提醒接口把互不依赖的查询段串行执行,每段各持一条
     * Hikari 连接排队(默认 10 连接下池吞吐被拖到 30-100 RPS)。虚拟线程按任务创建、
     * 阻塞等待 JDBC 时不占用平台线程,且天然 daemon 不阻止 JVM 退出;随进程生命周期,不 shutdown。
     */
    private static final ExecutorService READ_POOL = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("reminder-readonly-", 0).factory());


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
    private void applyOwnerScope(LambdaQueryWrapper<BizReminder> wrapper, Long ownerId, List<Long> visible) {
        // 【容量评估修复§2】visible 改由调用方预先解析后传入(与 deptId 子树展开并发执行,消除串行瀑布一段):
        // resolveModuleUsers 的调用与下面的过滤分支逐行保持原实现,仅解析时机前移,语义不变。
        // (【2026-09-19 接入权限矩阵】(reminder,view) 显式配置优先，未配置回退角色数据范围)
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
        // 【容量评估修复§2 串行段并发】deptId 子树展开(expandSubtree 递归查询)与数据范围解析
        // (resolveModuleUsers) 互不依赖,原实现串行执行各持连接排队;二者并发后再组装查询条件。
        // 两个调用的入参/内部逻辑/过滤语义完全不变,仅调度方式由串行改并发。
        List<Object> pre = supplyConcurrently(List.of(
                (Supplier<Object>) () -> deptId != null
                        ? com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId) : null,
                (Supplier<Object>) () -> scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder")
        ));
        if (deptId != null) {
            @SuppressWarnings("unchecked") List<Long> subtree = (List<Long>) pre.get(0);
            wrapper.in(BizReminder::getOfficeId, subtree);
        }
        applyOwnerScope(wrapper, ownerId, (List<Long>) pre.get(1));
        wrapper.orderByDesc(BizReminder::getCreatedAt);

        IPage<BizReminder> page = reminderMapper.selectPage(new Page<>(current, size), wrapper);

        // 【容量评估修复§1 N+1 批量化】原循环内每行 3 次 selectById 点查(customer/user/dept),
        // size=15 一页即 ~45 条串行 SQL、持连接 100-300ms——容量评估公认第一瓶颈。
        // 改为: 收集本页去重 id → 每维一次 selectBatchIds → Map 回填。
        // 字段写入与"查不到则跳过"的缺省行为同逐行版;回填按 records 原序二次遍历,行序不变。
        List<BizReminder> rows = page.getRecords();
        Set<Long> customerIds = new HashSet<>();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> officeIds = new HashSet<>();
        for (BizReminder r : rows) {
            if (r.getCustomerId() != null) customerIds.add(r.getCustomerId());
            if (r.getOwnerId() != null) ownerIds.add(r.getOwnerId());
            if (r.getOfficeId() != null) officeIds.add(r.getOfficeId());
        }
        // 三个回填查询互不依赖 → 并发执行;本页 SQL 由 3×size 条降为 ≤3 条(个位数,要求§1)
        Supplier<Map<Long, ?>> customerFill = () ->
                batchIdMap(customerIds, () -> customerMapper.selectBatchIds(customerIds), BizCustomer::getId);
        Supplier<Map<Long, ?>> ownerFill = () ->
                batchIdMap(ownerIds, () -> userMapper.selectBatchIds(ownerIds), SysUser::getId);
        Supplier<Map<Long, ?>> officeFill = () ->
                batchIdMap(officeIds, () -> deptMapper.selectBatchIds(officeIds), SysDept::getId);
        List<Map<Long, ?>> fills = supplyConcurrently(List.of(customerFill, ownerFill, officeFill));
        @SuppressWarnings("unchecked") Map<Long, BizCustomer> customerMap = (Map<Long, BizCustomer>) fills.get(0);
        @SuppressWarnings("unchecked") Map<Long, SysUser> userMap = (Map<Long, SysUser>) fills.get(1);
        @SuppressWarnings("unchecked") Map<Long, SysDept> deptMap = (Map<Long, SysDept>) fills.get(2);
        for (BizReminder r : rows) {
            if (r.getCustomerId() != null) {
                BizCustomer customer = customerMap.get(r.getCustomerId());
                if (customer != null) {
                    r.setCustomerName(customer.getCustomerName());
                    r.setLastInvoiceDate(customer.getLastInvoiceDate());
                }
            }
            if (r.getOwnerId() != null) {
                SysUser user = userMap.get(r.getOwnerId());
                if (user != null) r.setOwnerName(user.getRealName());
            }
            if (r.getOfficeId() != null) {
                SysDept dept = deptMap.get(r.getOfficeId());
                if (dept != null) r.setOfficeName(dept.getDeptName());
            }
        }
        // 【F15① 2026-10-18 用户裁决·删除是终态】软删客户的孤儿提醒不再出现在列表:
        // 应用内删客户会清 pending 提醒(INV-05),但 SQL 直删(如 8-10 的 290 条)绕过该清理,
        // 残留提醒在"到期提醒页/角标"(查客户表,自动滤已删)看不到、在本列表(查提醒表原始行)却照常
        // 出现——两接口口径分叉。此处的 customerMap 来自 selectBatchIds(@TableLogic 已排除已删客户),
        // customer_id 查不到的行即孤儿行,整行滤除并与 total 对齐;与 expiringPage/pendingCount 同口径。
        if (!rows.isEmpty()) {
            long orphans = rows.stream().filter(r ->
                    r.getCustomerId() != null && !customerMap.containsKey(r.getCustomerId())).count();
            if (orphans > 0) {
                rows.removeIf(r ->
                        r.getCustomerId() != null && !customerMap.containsKey(r.getCustomerId()));
                page.setTotal(Math.max(0, page.getTotal() - orphans));
            }
        }
        return page;
    }

    public IPage<Map<String, Object>> expiringPage(int current, int size, String keyword,
                                                     Long officeId, Long ownerId, Long deptId) {
        // 【容量评估修复§2 四段串行瀑布→并行】原实现 ①warningDays 配置读 → ②deptId 子树递归展开
        // → ③数据范围解析(resolveModuleUsers,内部含部门树/成员/开关多条查询) → ④客户分页,四段串行
        // 各持连接排队(历史结论: DB 无罪,慢在串行段与 N+1 点查)。①②③互不依赖,并发执行后再组装
        // ④ 的查询条件;三段各自的调用方式与内部语义完全不变,仅调度由串行改并发。
        List<Object> pre = supplyConcurrently(List.of(
                (Supplier<Object>) () -> resolveWarningDays(),
                (Supplier<Object>) () -> deptId != null
                        ? com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId) : null,
                (Supplier<Object>) () -> scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "reminder")
        ));
        int warningDays = (Integer) pre.get(0);

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        LocalDate deadline = LocalDate.now().plusDays(warningDays);
        wrapper.isNotNull(BizCustomer::getExpireDate)
                .le(BizCustomer::getExpireDate, deadline)
                .notIn(BizCustomer::getStatus, "sea", "archived") // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .orderByAsc(BizCustomer::getExpireDate);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(BizCustomer::getCustomerName, keyword);
        }
        if (deptId != null) {
            @SuppressWarnings("unchecked") List<Long> subtree = (List<Long>) pre.get(1);
            wrapper.in(BizCustomer::getOfficeId, subtree);
        } else if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }

        // 按数据范围过滤负责人（同 page() 修复，替换原"非管理员只看自己"的粗粒度判断；
        // 2026-09-19 接入权限矩阵：(reminder,view) 显式配置优先。
        // 【容量评估修复§2】visibleOwners 改由上方并发段预解析,下方过滤分支与原实现逐行一致）
        List<Long> visibleOwners = (List<Long>) pre.get(2);
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

        // 【容量评估修复§1 N+1 批量化】原循环内每行 4 查(dept/user 点查 + pending/processed 各一次
        // selectOne),size=15 一页即 ~60 条串行 SQL。改为整页收集后 4 次批量查询并发执行。
        // 语义等价: 原 selectOne 为 eq(customerId)+orderByDesc(id) LIMIT 1,即"该客户下 id 最大的
        // 一条";批量版 in(customerIds) 一次取回后内存取每客户 max(id)(latestReminderPerCustomer),
        // 结果与逐行版一致。行字段写入/键缺省行为("查不到不 put 该键")亦与逐行版一致。
        List<BizCustomer> customers = customerPage.getRecords();
        Set<Long> customerIds = new HashSet<>();
        Set<Long> officeIds = new HashSet<>();
        Set<Long> ownerIds = new HashSet<>();
        for (BizCustomer c : customers) {
            if (c.getId() != null) customerIds.add(c.getId()); // 主键,分页行内正常恒非空,防御跳过
            if (c.getOfficeId() != null) officeIds.add(c.getOfficeId());
            if (c.getOwnerId() != null) ownerIds.add(c.getOwnerId());
        }
        LocalDateTime todayStart = today.atStartOfDay();
        Supplier<Object> deptFill = () ->
                batchIdMap(officeIds, () -> deptMapper.selectBatchIds(officeIds), SysDept::getId);
        Supplier<Object> userFill = () ->
                batchIdMap(ownerIds, () -> userMapper.selectBatchIds(ownerIds), SysUser::getId);
        Supplier<Object> pendingFill = () -> customerIds.isEmpty() ? Map.<Long, BizReminder>of()
                : latestReminderPerCustomer(reminderMapper.selectList(new LambdaQueryWrapper<BizReminder>()
                        .in(BizReminder::getCustomerId, customerIds)
                        .eq(BizReminder::getReminderType, "expiring")
                        .eq(BizReminder::getStatus, "pending")));
        Supplier<Object> processedFill = () -> customerIds.isEmpty() ? Map.<Long, BizReminder>of()
                : latestReminderPerCustomer(reminderMapper.selectList(new LambdaQueryWrapper<BizReminder>()
                        .in(BizReminder::getCustomerId, customerIds)
                        .eq(BizReminder::getReminderType, "expiring")
                        .eq(BizReminder::getStatus, "processed")
                        .ge(BizReminder::getProcessedAt, todayStart)));
        List<Object> fills = supplyConcurrently(List.of(deptFill, userFill, pendingFill, processedFill));
        @SuppressWarnings("unchecked") Map<Long, SysDept> deptMap = (Map<Long, SysDept>) fills.get(0);
        @SuppressWarnings("unchecked") Map<Long, SysUser> userMap = (Map<Long, SysUser>) fills.get(1);
        @SuppressWarnings("unchecked") Map<Long, BizReminder> pendingMap = (Map<Long, BizReminder>) fills.get(2);
        @SuppressWarnings("unchecked") Map<Long, BizReminder> processedMap = (Map<Long, BizReminder>) fills.get(3);

        List<Map<String, Object>> records = customers.stream().map(c -> {
            Map<String, Object> row = new HashMap<>();
            row.put("customerId", c.getId());
            row.put("customerName", c.getCustomerName());
            row.put("expireDate", c.getExpireDate());
            row.put("remainingDays", ChronoUnit.DAYS.between(today, c.getExpireDate()));

            // 【容量评估修复§1】dept/user 由逐行 selectById 改查批量 Map;字段写入与缺省行为同原版
            if (c.getOfficeId() != null) {
                SysDept dept = deptMap.get(c.getOfficeId());
                if (dept != null) row.put("officeName", dept.getDeptName());
            }
            if (c.getOwnerId() != null) {
                SysUser user = userMap.get(c.getOwnerId());
                if (user != null) row.put("ownerName", user.getRealName());
            }

            // 跟进状态(【2026-09-23 去重口径统一】随 ensureExpiring 改为"pending 存在即不新建"后,
            // 今日行可能不存在:reminderId 指向当前 pending(无则今日已处理行),todayFollowedUp=今日处理过)
            // 【容量评估修复§1】两次逐行 selectOne 改查预取好的每客户 max(id) Map,判定逻辑不变
            BizReminder currentPending = c.getId() == null ? null : pendingMap.get(c.getId());
            BizReminder todayProcessed = c.getId() == null ? null : processedMap.get(c.getId());
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

    /**
     * 【容量评估修复§2 通用并发段调度】把互不依赖的只读查询段并发执行,消除串行瀑布。
     * 约束(与修复要求§2逐条对齐):
     * <ul>
     *   <li>仅用于无活动事务的调用方:每条查询本就独立从连接池取连接、自动提交,并发执行
     *       不改变任何一条语句的事务/连接语义,也不引入新事务边界。若当前线程恰好处于活动事务
     *       (isActualTransactionActive,连接绑定在线程 ThreadLocal 上),自动回退串行执行,
     *       保证与旧实现完全相同的连接语义;</li>
     *   <li>工作线程显式挂载/清理调用方 SecurityContext:租户拦截器
     *       (MybatisPlusConfig.TenantLineInnerInterceptor)在 SQL 改写时按 ThreadLocal 读当前用户,
     *       不传播会导致非 admin 查询被改写为 tenant_id=NULL 恒空(数据静默丢失);</li>
     *   <li>异常解包还原为原异常抛出,调用方看到的异常类型与串行版一致;</li>
     *   <li>单段任务无并发收益,保持原串行路径,不引入线程切换。</li>
     * </ul>
     */
    private <T> List<T> supplyConcurrently(List<Supplier<T>> tasks) {
        if (tasks.size() == 1
                || TransactionSynchronizationManager.isActualTransactionActive()) {
            List<T> inline = new ArrayList<>(tasks.size());
            for (Supplier<T> task : tasks) inline.add(task.get());
            return inline;
        }
        SecurityContext callerContext = SecurityContextHolder.getContext();
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Supplier<T> task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(callerContext); // 见 javadoc: 租户拦截器依赖
                try {
                    return task.get();
                } finally {
                    SecurityContextHolder.clearContext(); // 虚拟线程不复用,清引用防泄漏
                }
            }, READ_POOL));
        }
        try {
            List<T> results = new ArrayList<>(tasks.size());
            for (CompletableFuture<T> f : futures) results.add(f.join());
            return results;
        } catch (CompletionException ce) {
            if (ce.getCause() instanceof RuntimeException re) throw re; // 还原原异常,行为同串行版
            if (ce.getCause() instanceof Error err) throw err;
            throw ce;
        }
    }

    /**
     * 【容量评估修复§1】按去重 id 集批量加载并转 id→实体 Map;id 集为空时直接返回空 Map
     * 不发 SQL(同原实现"该维全为 null 时本就不查询"的行为)。
     */
    private <T> Map<Long, T> batchIdMap(Collection<Long> ids, Supplier<List<T>> loader, Function<T, Long> idGetter) {
        if (ids == null || ids.isEmpty()) return new HashMap<>();
        Map<Long, T> map = new HashMap<>();
        List<T> rows = loader.get();
        if (rows == null) return map; // 防御:mock/异常实现返回 null(真实 BaseMapper 不为 null)
        for (T row : rows) {
            Long id = row == null ? null : idGetter.apply(row);
            if (id != null) map.putIfAbsent(id, row); // 主键无重复,遇脏重复取首条
        }
        return map;
    }

    /**
     * 【容量评估修复§1】按客户分组取"每客户 id 最大"的提醒,等价原逐行
     * selectOne(...orderByDesc(id).last("LIMIT 1"))——同一客户多条时 DB 倒序取首条
     * ⇔ 内存取 max(id)。null id 视为最小(自增主键正常恒非空,纯防御)。
     */
    private static Map<Long, BizReminder> latestReminderPerCustomer(List<BizReminder> rows) {
        Map<Long, BizReminder> map = new HashMap<>();
        if (rows == null) return map; // 防御:同上
        for (BizReminder r : rows) {
            if (r == null || r.getCustomerId() == null) continue;
            BizReminder cur = map.get(r.getCustomerId());
            if (cur == null || idOf(r) >= idOf(cur)) map.put(r.getCustomerId(), r);
        }
        return map;
    }

    private static long idOf(BizReminder r) {
        return r.getId() == null ? Long.MIN_VALUE : r.getId();
    }

    /**
     * 【容量评估修复§2】读取 customer.warning.days 提前天数;缺失/脏值回退默认(解析失败不阻断查询)。
     * 自 expiringPage 内联逻辑原样抽出,以便作为独立并发段执行,解析语义不变。
     */
    private int resolveWarningDays() {
        String val = configService.getValue("customer.warning.days");
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认天数
            }
        }
        return DEFAULT_WARNING_DAYS;
    }
}
