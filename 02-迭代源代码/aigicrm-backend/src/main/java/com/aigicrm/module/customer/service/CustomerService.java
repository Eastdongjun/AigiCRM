package com.aigicrm.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcelFactory;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.PermissionRuleEngine;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.entity.*;
import com.aigicrm.module.customer.listener.CustomerImportListener;
import com.aigicrm.module.customer.mapper.*;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.reminder.entity.BizReminder;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_CUSTOMER = "customer";
    private static final String LIT_ARCHIVED = "archived";
    private static final String LIT_NORMAL = "normal";
    private static final String LIT_ACTIVE = "active";
    private static final String LIT_PENDING = "pending";
    private static final String LIT_X = "客户不存在";
    private static final String LIT_SHARE_TO = "share_to";
    private static final String LIT_VOIDED = "voided";
    private static final String LIT_X_2 = "发票不存在或不属于该客户";
    private static final String LIT_X_3 = "回款不存在或不属于该客户";
    private static final String LIT_LIMIT_1 = "LIMIT 1";


    /** 「所属集团」下拉候选返回上限（防大数据量下拉过长，超出的集团名仍可手工输入新建） */
    private static final int GROUP_NAME_LIMIT = 200;

    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizCustomerStageAmountMapper stageAmountMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final JdbcTemplate jdbc;
    /** S5.3 分享/联系人独立 bean；本类保留公共委托。 */
    private final CustomerContactShareService customerContactShareService;
    /** S5.3 数据范围/自动共享独立 bean；本类保留条件拼装委托。 */
    private final CustomerScopeService customerScopeService;
    /** S5.3 列表/计数/导出/名称回填独立 bean；本类保留公共委托。 */
    private final CustomerQueryService customerQueryService;
    /** S5.3 五项指标/核心判定独立 bean；本类保留公共委托。 */
    private final CustomerIndicatorService customerIndicatorService;
    /** S5.3 保护期/到期日公式独立 bean。 */
    private final CustomerLifecycleService customerLifecycleService;
    /** S5.3 发票/回款台账独立 bean；本类保留公共委托。 */
    private final CustomerFinanceService customerFinanceService;
    /** S5.3 客户/财务审计落库独立 bean。 */
    private final CustomerAuditService customerAuditService;
    /** S5.3 建/改/删/导入独立 bean；本类保留公共委托。 */
    private final CustomerWriteService customerWriteService;

    /**
     * 【容量加固·客户列表服务端分页 2026-10-05】列表统一查询条件对象:
     * 分页(pageList)/类型页签计数(countByDevType)/导出(exportList)三口径共用一份条件拼装,
     * 取代"PC 前端一次拉 500 条 + 本地三层过滤"模式(容量评估流量模型高危项)。
     * userIds 与 ownerName 为同列的两个独立 AND 条件(对齐本地 filterCustomers 语义);
     * officeIds 对齐本地 orgIds:所属组织单元命中 office 或 dept 即算。
     */
    /** 服务端分页查询(新口径入口) */
    // ====== 列表/计数/导出（S5.3 语义已移到 CustomerQueryService） ======

    public IPage<BizCustomer> pageList(CustomerListFilter f, int current, int size) {
        return customerQueryService.pageList(f, current, size);
    }

    public Map<String, Long> countByDevType(CustomerListFilter f) {
        return customerQueryService.countByDevType(f);
    }

    public List<BizCustomer> exportList(CustomerListFilter f) {
        return customerQueryService.exportList(f);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, null, null, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, null, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, devType, groupName, null);
    }

    @SuppressWarnings("unused") // else-if(false) 设计历史分支为监督面板强制保留(见下), 抑制 Dead code 告警
    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName, Long deptId) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, devType, groupName, deptId, null, null, null);
    }

    /**
     * 【对齐 PC 高级筛选 2026-09-21】protectionStatus 精确 / province / city LIKE。
     * 【容量加固 2026-10-05】本重载保留作兼容入口(条件拼装已统一迁入 buildListWrapper/applyFilterConditions,
     * 供 pageList/countByDevType/exportList 三口径共用;shared_to_me/shared_by_me 分支亦在其中统一处理)。
     */
    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName, Long deptId,
                                    String protectionStatus, String province, String city) {
        return pageList(new CustomerListFilter(keyword, industry, officeId, regionId, deptId, status, ownerId, scope,
                devType, groupName, protectionStatus, province, city,
                null, null, null, null, null, null, null), current, size);
    }

    // 【容量加固 2026-10-05】pageSharedToMe/pageSharedByMe/applyCommonFilters 三方法已删除:
    // 分享视图的 scope 分支与全量条件统一并入 buildListWrapper/applyFilterConditions(三口径共用)。

    public Map<String, Long> listScopeCounts() {
        return customerQueryService.listScopeCounts();
    }

    public List<String> listGroupNames() {
        return customerQueryService.listGroupNames();
    }

    public List<Map<String, Object>> getSimpleUserList() {
        return customerQueryService.getSimpleUserList();
    }

    public BizCustomer getById(Long id) {
        BizCustomer customer = customerMapper.selectById(id);
        // 【2026-08-14 404 防探测】不存在与无权访问统一文案, 消除存在性 oracle(GlobalExceptionHandler 按 code=404 转 HTTP 404)
        if (customer == null) {
            throw new BusinessException(404, "客户不存在或无权访问");
        }
        customerQueryService.fillCustomerNames(java.util.List.of(customer));
        return customer;
    }

    // ====== 建/改/删/导入（S5.3 语义已移到 CustomerWriteService） ======

    @Transactional
    public void create(BizCustomer customer) {
        customerWriteService.create(customer);
    }

    public boolean existsByNameGlobal(String customerName, Long excludeId) {
        return customerWriteService.existsByNameGlobal(customerName, excludeId);
    }

    public boolean existsByName(String customerName) {
        return customerWriteService.existsByName(customerName);
    }

    public boolean existsByName(String customerName, Long excludeId) {
        return customerWriteService.existsByName(customerName, excludeId);
    }

    public CustomerImportResult importCustomers(MultipartFile file) {
        return customerWriteService.importCustomers(file);
    }

    @Transactional
    public void update(BizCustomer customer) {
        customerWriteService.update(customer);
    }

    @Transactional
    public void delete(Long id) {
        customerWriteService.delete(id);
    }

    @Transactional
    public void deleteFromSeaPool(Long id) {
        customerWriteService.deleteFromSeaPool(id);
    }

    public void assertDeletable(Long id) {
        customerWriteService.assertDeletable(id);
    }

    public void assertNoActiveApprovals(Long id) {
        customerWriteService.assertNoActiveApprovals(id);
    }

    public void archive(Long id) {
        customerWriteService.archive(id);
    }

    public void restore(Long id) {
        customerWriteService.restore(id);
    }

    // ====== 自动共享规则（S5.3 语义已移到 CustomerScopeService） ======

    private void applyDataScopeAndAutoShareFor(LambdaQueryWrapper<BizCustomer> wrapper,
                                               com.aigicrm.security.SecurityUser scopeUser) {
        customerScopeService.applyDataScopeAndAutoShareFor(wrapper, scopeUser);
    }

    public List<BizCustomerLog> getLogs(Long customerId) {
        LambdaQueryWrapper<BizCustomerLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomerLog::getCustomerId, customerId)
                .orderByDesc(BizCustomerLog::getCreatedAt);
        List<BizCustomerLog> logs = customerLogMapper.selectList(wrapper);
        for (BizCustomerLog log : logs) {
            SysUser user = userMapper.selectById(log.getUserId());
            if (user != null) log.setUserName(user.getRealName());
        }
        return logs;
    }

    public void addLog(BizCustomerLog log) {
        log.setUserId(SecurityUtils.getCurrentUserId());
        customerLogMapper.insert(log);
    }

    // 统计数据 for 工作台（受数据范围控制）
    public long countByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /**
     * 【2026-11-03 权限中心·生效预览】按任意目标用户的 customer×view 范围数客户(排 sea/archived, 与 countByScope 同口径)。
     * 与 applyDataScope 的差异: ①目标用户作参数(不绑当前登录人) ②不含运行时规则引擎追加条件
     * (预览只表达范围层, 规则条件属另一维度, 不在此复刻)——预览数字为范围口径的如实呈现。
     */
    public long countByScopeFor(com.aigicrm.security.SecurityUser target) {
        if (target == null) return 0;
        // 【D2 修复 2026-11-16】旧实现自己拼 ScopeRequest，与列表路径(applyDataScope+autoShare+规则引擎)
        // 是两套口径：TY005(data_scope_override=office) 预览 121 vs 列表 17。现改为复用执行侧同一方法，
        // 预览数字=列表真实可见数（执行侧为准）。
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScopeAndAutoShareFor(w, target);
        return customerMapper.selectCount(w);
    }

    public long countInProtectionByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        // 【2026-11-01 审计修复】排公海/归档——归档只改 status 不重置 protectionStatus, 不排则
        // 工作台"在保护期/即将到期"与统计页同指标(已排)数字错位; 口径与同文件已超期统计对齐
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, LIT_ACTIVE)
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiringByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expiring")
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 当日到期客户数（受数据范围控制） */
    public long countExpiringTodayByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0)
                .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .eq(BizCustomer::getExpireDate, java.time.LocalDate.now());
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiredByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        // 【口径统一 2026-10-09 用户裁决】工作台「已超期」= expireDate<今日 且排除公海/归档——
        // 与统计页 StatisticsQueryService 同判据(此前 protection_status='expired' 不排公海,
        // 工作台29 vs 统计页2 两页数字不一致; 且到期客户实际已被凌晨任务转 sea+expired,
        // 排除公海后 protection_status 等值与日期判据唯一差异是"任务跑过没有", 日期版确定性更高)
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED)
                .lt(BizCustomer::getExpireDate, java.time.LocalDate.now());
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 本月新增客户数（受数据范围控制） */
    public long countNewThisMonth() {
        java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                .ge(BizCustomer::getCreatedAt, monthStart);
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 按周统计本月新增客户（受数据范围控制） */
    public java.util.List<java.util.Map<String, Object>> weeklyNewCustomers() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = today.withDayOfMonth(1);
        java.util.List<java.util.Map<String, Object>> weeks = new java.util.ArrayList<>();
        int wn = 1;
        while (!start.isAfter(today)) {
            java.time.LocalDate end = start.plusDays(6);
            boolean isCurrent = !end.isBefore(today);
            if (end.isAfter(today)) end = today;
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                    .ge(BizCustomer::getCreatedAt, start.atStartOfDay())
                    .lt(BizCustomer::getCreatedAt, end.plusDays(1).atStartOfDay());
            applyDataScope(w);
            long count = customerMapper.selectCount(w);
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("label", isCurrent ? "本周" : "W" + wn);
            item.put("value", count);
            weeks.add(item);
            start = start.plusDays(7);
            wn++;
        }
        return weeks;
    }

    /** 按天统计本月新增客户（受数据范围控制） */
    public java.util.List<java.util.Map<String, Object>> dailyNewCustomers() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = today.withDayOfMonth(1);
        java.util.List<java.util.Map<String, Object>> days = new java.util.ArrayList<>();
        java.time.LocalDate cursor = start;
        while (!cursor.isAfter(today)) {
            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
                    .ge(BizCustomer::getCreatedAt, cursor.atStartOfDay())
                    .lt(BizCustomer::getCreatedAt, cursor.plusDays(1).atStartOfDay());
            applyDataScope(w);
            long count = customerMapper.selectCount(w);
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("day", cursor.getDayOfMonth());
            item.put("date", cursor.toString());
            item.put("value", count);
            days.add(item);
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    /**
     * 获取范围内即将到期的客户（前5）
     */
    public List<BizCustomer> getExpiringByScope(int limit) {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expiring")
                .orderByAsc(BizCustomer::getExpireDate).last("LIMIT " + limit);
        applyDataScope(w);
        List<BizCustomer> list = customerMapper.selectList(w);
        customerQueryService.fillCustomerNames(list);
        return list;
    }

    /**
     * 获取当前用户数据范围内的有效客户（未删除、非公海）
     * 【修复 FEAT-20260804-INDICATOR-SCOPE】供 StatisticsQueryService 等跨模块聚合报表复用，
     * 避免各处各自直接 selectList(null) 绕过数据范围导致全公司数据越权可见
     */
    public List<BizCustomer> getScopedActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        applyDataScope(wrapper);
        return customerMapper.selectList(wrapper);
    }

    /** 【2026-08-08 全员可见】全部启用客户（核心客户六项指标等公司级展示用，不做数据范围过滤） */
    public List<BizCustomer> getAllActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        return customerMapper.selectList(wrapper);
    }

    public Map<String, Object> getStatistics() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        // 【2026-11-21 B3-PERF-001】只取聚合所需两列，避免 5 万客户整行/大字段加载；
        // 部门名称改为一次 selectBatchIds，消除按 office 的 N+1 查询。
        wrapper.select(BizCustomer::getIndustry, BizCustomer::getOfficeId)
                .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived

        // 数据范围过滤（统计也受范围控制）
        applyDataScope(wrapper);

        List<BizCustomer> customers = customerMapper.selectList(wrapper);

        // 空白行业归类为"未填写",避免统计出现无前缀空键(与 StatisticsQueryService 口径一致)
        Map<String, Long> industryStats = customers.stream()
                .map(c -> (c.getIndustry() == null || c.getIndustry().isBlank()) ? "未填写" : c.getIndustry().trim())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        Map<Long, Long> officeStats = customers.stream()
                .filter(c -> c.getOfficeId() != null)
                .collect(Collectors.groupingBy(BizCustomer::getOfficeId, Collectors.counting()));

        List<Map<String, Object>> officeList = new ArrayList<>();
        Map<Long, String> officeNames = new HashMap<>();
        if (!officeStats.isEmpty()) {
            for (var dept : deptMapper.selectBatchIds(officeStats.keySet())) {
                officeNames.put(dept.getId(), dept.getDeptName());
            }
        }
        officeStats.forEach((oid, count) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("officeId", oid);
            item.put("officeName", officeNames.getOrDefault(oid, "未知"));
            item.put("count", count);
            officeList.add(item);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("total", customers.size());
        result.put("industryStats", industryStats);
        result.put("officeStats", officeList);
        return result;
    }

    // 【P0-2 2026-11-12 外部审计 HIGH 退役】customerRankings 及三段排行原生 SQL(激励/客情/核心指标)
    // 已随休眠端点 GET /api/customer/rankings 整体删除——仅集团过滤无数据范围/分享制收口(越权面);
    // 现行排行=StatisticsQueryService.customerRankings(applyDataScope 收口)。
    // groupCondition 集团条件片段随之失去唯一调用方, 一并退役(防死代码)。

    // ====== 数据范围（S5.3 语义已移到 CustomerScopeService） ======

    private void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        customerScopeService.applyDataScope(wrapper);
    }

    public List<Long> getCurrentScopeUserIds() {
        return customerScopeService.getCurrentScopeUserIds();
    }

    // ====== 捞取/延期（S5.3 语义已移到 CustomerLifecycleService） ======

    @Transactional
    public void applyClaimReset(BizCustomer customer, LocalDate claimDate) {
        customerLifecycleService.applyClaimReset(customer, claimDate);
    }

    @Transactional
    public void applyExtendApproval(BizCustomer customer, int extendDays) {
        customerLifecycleService.applyExtendApproval(customer, extendDays);
    }

    // ====== 开票记录 ======

    // ====== 开票/回款（S5.3 语义已移到 CustomerFinanceService） ======

    public List<BizInvoice> getInvoices(Long customerId) {
        return customerFinanceService.getInvoices(customerId);
    }

    public Map<String, Object> getInvoiceSummary(Long customerId) {
        return customerFinanceService.getInvoiceSummary(customerId);
    }

    public void addInvoice(Long customerId, BizInvoice invoice) {
        customerFinanceService.addInvoice(customerId, invoice);
    }

    public void confirmInvoice(Long customerId, Long invoiceId) {
        customerFinanceService.confirmInvoice(customerId, invoiceId);
    }

    public void voidInvoice(Long customerId, Long invoiceId, String reason) {
        customerFinanceService.voidInvoice(customerId, invoiceId, reason);
    }

    public void deleteInvoice(Long customerId, Long invoiceId, String reason) {
        customerFinanceService.deleteInvoice(customerId, invoiceId, reason);
    }

    // ====== 客户指标 ======

    // ====== 客户指标（S5.3 语义已移到 CustomerIndicatorService） ======

    public BizCustomerIndicator getIndicator(Long customerId) {
        return customerIndicatorService.getIndicator(customerId);
    }

    @Transactional
    public void saveIndicator(Long customerId, BizCustomerIndicator data) {
        customerIndicatorService.saveIndicator(customerId, data);
    }

    /**
     * 【PR-05 2026-09-19】客户与本次客情指标同事务统一提交(新建)。
     * 原两步保存(建客户→存指标)在第二步失败时产生"部分成功、界面报保存失败"，
     * 用户重试可能重复建客户。组合方法单一事务：任一步失败整体回滚，界面失败即全未保存。
     */
    @Transactional
    public Long createWithIndicator(BizCustomer customer, BizCustomerIndicator indicator) {
        customerWriteService.create(customer);
        if (indicator != null) {
            customerIndicatorService.saveIndicator(customer.getId(), indicator);
        }
        return customer.getId();
    }

    /** 【PR-05】同上，编辑态组合提交。 */
    @Transactional
    public void updateWithIndicator(Long id, BizCustomer customer, BizCustomerIndicator indicator) {
        customer.setId(id);
        customerWriteService.update(customer);
        if (indicator != null) {
            customerIndicatorService.saveIndicator(id, indicator);
        }
    }

    public static final String CORE_MINOR_WAN_KEY = CustomerIndicatorService.CORE_MINOR_WAN_KEY;
    public static final String CORE_MAJOR_WAN_KEY = CustomerIndicatorService.CORE_MAJOR_WAN_KEY;

    @Transactional
    public void saveRelationshipScore(Long customerId, int score) {
        customerIndicatorService.saveRelationshipScore(customerId, score);
    }

    @Transactional
    public int recalculateAllCoreStatuses() {
        return customerIndicatorService.recalculateAllCoreStatuses();
    }

    void recalculateCoreStatus(Long customerId) {
        customerIndicatorService.recalculateCoreStatus(customerId);
    }

    // ====== 阶段开票回款(2026-10-10 半年/全年总额录入制) ======

    private static final Set<String> PERIOD_TYPES = Set.of("H1", "H2", "FY");
    private static final int PERIOD_YEAR_MIN = 2000;

    /** 周期展示名: 与用户口径一致——2026上半年 / 2026下半年 / 2026全年 */
    private String periodLabel(int year, String type) {
        String suffix = switch (type) {
            case "H1" -> "上半年";
            case "H2" -> "下半年";
            default -> "全年";
        };
        return year + suffix;
    }

    /**
     * 阶段列表 + 判定摘要(一处算好, PC/MP 两端直接展示, 避免口径两端复刻漂移)。
     * 返回: rows(年倒序, 年内 全年>下半年>上半年), baseYear/annualInvoiceAmount/annualPaymentAmount/coreType,
     * 以及当前双门槛 coreMinorWan/coreMajorWan(万元, 前端门槛文案不落死值)。
     */
    public Map<String, Object> getStageAmountSummary(Long customerId) {
        List<BizCustomerStageAmount> rows = stageAmountMapper.selectList(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, customerId));
        Map<String, String> nameById = new HashMap<>();
        rows.stream().map(BizCustomerStageAmount::getUpdatedBy).filter(Objects::nonNull).distinct().forEach(uid -> {
            SysUser u = userMapper.selectById(uid);
            if (u != null) nameById.put(String.valueOf(uid), u.getRealName());
        });
        rows.forEach(r -> {
            r.setPeriodLabel(periodLabel(r.getPeriodYear(), r.getPeriodType()));
            r.setUpdatedByName(r.getUpdatedBy() == null ? null : nameById.get(String.valueOf(r.getUpdatedBy())));
        });
        // 年倒序; 年内 全年(FY) > 下半年(H2) > 上半年(H1)
        rows.sort((a, b) -> {
            int byYear = Integer.compare(b.getPeriodYear(), a.getPeriodYear());
            if (byYear != 0) return byYear;
            return Integer.compare(periodRank(b.getPeriodType()), periodRank(a.getPeriodType()));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", rows);
        // 复用已载入的 rows 判定(免二次查询); 双门槛一并回传, 前端门槛文案不落死值
        BigDecimal[] coreThresholds = customerIndicatorService.coreThresholds();
        result.put("coreType", CustomerIndicatorService.computeCoreTypeFromRows(rows, coreThresholds[0], coreThresholds[1]));
        result.put("coreMinorWan", coreThresholds[0]);
        result.put("coreMajorWan", coreThresholds[1]);
        result.put("baseYear", 0);
        result.put("annualInvoiceAmount", BigDecimal.ZERO);
        result.put("annualPaymentAmount", BigDecimal.ZERO);
        // 基准年与年度金额摘要(与 computeCoreType 同口径, 展示判定头用)
        int baseYear = rows.stream()
                .filter(r -> CustomerIndicatorService.isPositiveAmount(r.getInvoiceAmount()) || CustomerIndicatorService.isPositiveAmount(r.getPaymentAmount()))
                .mapToInt(BizCustomerStageAmount::getPeriodYear).max().orElse(0);
        if (baseYear > 0) {
            result.put("baseYear", baseYear);
            List<BizCustomerStageAmount> yearRows = rows.stream()
                    .filter(r -> r.getPeriodYear() == baseYear).toList();
            boolean fyExists = yearRows.stream().anyMatch(r -> "FY".equals(r.getPeriodType())
                    && (CustomerIndicatorService.isPositiveAmount(r.getInvoiceAmount()) || CustomerIndicatorService.isPositiveAmount(r.getPaymentAmount())));
            List<BizCustomerStageAmount> basis = fyExists
                    ? yearRows.stream().filter(r -> "FY".equals(r.getPeriodType())).toList()
                    : yearRows.stream().filter(r -> "H1".equals(r.getPeriodType()) || "H2".equals(r.getPeriodType())).toList();
            result.put("annualInvoiceAmount", basis.stream().map(r -> nvl(r.getInvoiceAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            result.put("annualPaymentAmount", basis.stream().map(r -> nvl(r.getPaymentAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return result;
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private int periodRank(String type) {
        return switch (type == null ? "" : type) { case "H1" -> 1; case "H2" -> 2; default -> 3; };
    }

    /**
     * 保存(录入/修改)一个周期的阶段总额。编辑制 upsert: 唯一键(customer, year, type)+FOR UPDATE 行锁,
     * 并发下读到-写入串行化。金额单位万元, 至少一项 > 0; 未填的一项按 0(即"该周期无回款"也是合法录入)。
     * 审计: 改动快照落 sys_log。保护期接力: 开票额>0 时以阶段末日期(H1→6-30, H2/FY→12-31, 不晚于今天)
     * 前移刷新 lastInvoiceDate(只前进不后退) 并重算到期日——逐笔录入下线后保护期由阶段录入接力。
     */
    @Transactional
    public void saveStageAmount(Long customerId, com.aigicrm.module.customer.dto.StageAmountRequest req) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new BusinessException(LIT_X);
        StageInput input = validateStageInput(req);
        BizCustomerStageAmount existing = stageAmountMapper.selectByPeriodForUpdate(customerId, input.year(), input.type());
        String before = existing == null ? "无(首次录入)"
                : "开票 " + existing.getInvoiceAmount() + " 万 / 回款 " + existing.getPaymentAmount() + " 万";
        Long uid = SecurityUtils.getCurrentUserId();
        upsertStageAmount(customerId, input, existing, uid);
        customerAuditService.logRecordDeletion("客户", "保存阶段开票回款", "stage_amount", customerId,
                periodLabel(input.year(), input.type()) + ": " + before + " → 开票 " + input.invoice() + " 万 / 回款 " + input.payment() + " 万; customerId=" + customerId);
        if (input.invoice().signum() > 0) customerFinanceService.advanceInvoicePeriod(customer, customerId, input.year(), input.type());
        recalculateCoreStatus(customerId);
    }

    private record StageInput(String type, int year, BigDecimal invoice, BigDecimal payment) {}

    private StageInput validateStageInput(com.aigicrm.module.customer.dto.StageAmountRequest req) {
        String type = req.periodType() == null ? "" : req.periodType().trim();
        if (!PERIOD_TYPES.contains(type)) throw new BusinessException("周期类型无效(须为 上半年/下半年/全年)");
        int year = req.periodYear() == null ? 0 : req.periodYear();
        if (year < PERIOD_YEAR_MIN || year > LocalDate.now().getYear() + 1)
            throw new BusinessException("周期年份无效");
        BigDecimal invoice = nvl(req.invoiceAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal payment = nvl(req.paymentAmount()).setScale(2, java.math.RoundingMode.HALF_UP);
        if (invoice.signum() < 0 || payment.signum() < 0) throw new BusinessException("金额不能为负数");
        if (invoice.signum() == 0 && payment.signum() == 0)
            throw new BusinessException("阶段内开票额与阶段性回款额至少填一项且大于 0");
        if (invoice.compareTo(new BigDecimal("999999999999.99")) > 0
                || payment.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new BusinessException("金额超出上限");
        return new StageInput(type, year, invoice, payment);
    }

    private void upsertStageAmount(Long customerId, StageInput input, BizCustomerStageAmount existing, Long uid) {
        if (existing == null) {
            BizCustomerStageAmount row = new BizCustomerStageAmount();
            row.setCustomerId(customerId);
            row.setPeriodYear(input.year());
            row.setPeriodType(input.type());
            row.setInvoiceAmount(input.invoice());
            row.setPaymentAmount(input.payment());
            row.setCreatedBy(uid);
            row.setUpdatedBy(uid);
            stageAmountMapper.insert(row);
            return;
        }
        existing.setInvoiceAmount(input.invoice());
        existing.setPaymentAmount(input.payment());
        existing.setUpdatedBy(uid);
        stageAmountMapper.updateById(existing);
    }

    // ====== 客户分享/联系人（S5.3 语义已移到 CustomerContactShareService） ======

    public List<BizCustomerShare> getShares(Long customerId) {
        return customerContactShareService.getShares(customerId);
    }

    public void addShares(Long customerId, List<Long> userIds) {
        customerContactShareService.addShares(customerId, userIds);
    }

    public void removeShare(Long customerId, Long userId) {
        customerContactShareService.removeShare(customerId, userId);
    }

    public void removeAllShares(Long customerId) {
        customerContactShareService.removeAllShares(customerId);
    }

    public void cleanPendingReminders(Long customerId) {
        customerContactShareService.cleanPendingReminders(customerId);
    }

    public List<BizContact> getContacts(Long customerId) {
        return customerContactShareService.getContacts(customerId);
    }

    public void addContact(Long customerId, BizContact contact) {
        customerContactShareService.addContact(customerId, contact);
    }

    public void updateContact(Long customerId, Long contactId, BizContact contact) {
        customerContactShareService.updateContact(customerId, contactId, contact);
    }

    public void deleteContact(Long customerId, Long contactId) {
        customerContactShareService.deleteContact(customerId, contactId);
    }

    // ====== 回款记录 ======

    public List<BizPayment> getPayments(Long customerId) {
        return customerFinanceService.getPayments(customerId);
    }

    public Map<String, Object> getPaymentSummary(Long customerId) {
        return customerFinanceService.getPaymentSummary(customerId);
    }

    public void addPayment(Long customerId, BizPayment payment) {
        customerFinanceService.addPayment(customerId, payment);
    }

    public void deletePayment(Long customerId, Long paymentId, String reason) {
        customerFinanceService.deletePayment(customerId, paymentId, reason);
    }

    public java.util.Map<String, Object> getDuplicateInfo(String customerName) {
        // 【议题五.1】先按主名查,未命中再按别名查(裁决:查重仅提示,不自动合并)
        // 【修复 2026-08-10 P2-01】创建人/创建时间信息同样限定在数据范围内(与 existsByName 同口径)
        // 【PR-01 N-02 2026-09-18】原生 SQL 绕过租户拦截器：补 c.tenant_id 条件（biz_customer 在
        // 租户白名单内）；无集团归属直接返回 null（fail-closed，不泄露他租户创建人）。
        try {
            List<Long> scopeUserIds = getCurrentScopeUserIds();
            String tenantFrag;
            java.util.List<Object> tenantArgs = new java.util.ArrayList<>();
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (g == null) return null;
            tenantFrag = " AND c.tenant_id = ?";
            tenantArgs.add(g);
            java.util.List<java.util.Map<String, Object>> rows;
            if (scopeUserIds == null) {
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(customerName);
                args.addAll(tenantArgs);
                rows = jdbc.queryForList(
                    "SELECT u.real_name AS createdBy, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i') AS createdAt " +
                    "FROM biz_customer c JOIN sys_user u ON c.owner_id = u.id " +
                    "WHERE c.customer_name = ? AND c.deleted = 0" + tenantFrag + " ORDER BY c.created_at DESC LIMIT 1",
                    args.toArray());
            } else {
                java.util.List<Object> args = new java.util.ArrayList<>();
                args.add(customerName);
                args.addAll(tenantArgs);
                args.addAll(scopeUserIds);
                rows = jdbc.queryForList(
                    "SELECT u.real_name AS createdBy, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i') AS createdAt " +
                    "FROM biz_customer c JOIN sys_user u ON c.owner_id = u.id " +
                    "WHERE c.customer_name = ? AND c.deleted = 0" + tenantFrag + " AND c.owner_id IN (" +
                    String.join(",", java.util.Collections.nCopies(scopeUserIds.size(), "?")) +
                    ") ORDER BY c.created_at DESC LIMIT 1",
                    args.toArray());
            }
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
