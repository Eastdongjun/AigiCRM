package com.ycmn.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.ycmn.common.BusinessException;
import com.ycmn.common.engine.PermissionRuleEngine;
import com.ycmn.module.customer.dto.CustomerImportResult;
import com.ycmn.module.customer.dto.CustomerImportDTO;
import com.ycmn.module.customer.entity.*;
import com.ycmn.module.customer.listener.CustomerImportListener;
import com.ycmn.module.customer.mapper.*;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.reminder.entity.BizReminder;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUtils;
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

    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizCustomerShareMapper shareMapper;
    private final BizCustomerIndicatorMapper indicatorMapper;
    private final BizContactMapper contactMapper;
    private final BizPaymentMapper paymentMapper;
    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final SysDeptMapper deptMapper;
    private final BizReminderMapper reminderMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService configService;

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, null, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope) {
        return page(current, size, keyword, industry, officeId, regionId, status, ownerId, scope, null, null);
    }

    public IPage<BizCustomer> page(int current, int size, String keyword, String industry,
                                    Long officeId, Long regionId, String status, Long ownerId, String scope,
                                    String devType, String groupName) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // scope 筛选: mine / shared_by_me / shared_to_me
        if ("shared_to_me".equals(scope)) {
            return pageSharedToMe(current, size, keyword, industry, officeId, regionId, status, currentUserId);
        }
        if ("shared_by_me".equals(scope)) {
            return pageSharedByMe(current, size, keyword, industry, officeId, regionId, status, currentUserId);
        }

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0);

        if ("mine".equals(scope)) {
            wrapper.eq(BizCustomer::getOwnerId, currentUserId);
        } else if (false) { // disabled: all users use data scope
            // 默认：自己的客户 + 分享给我的客户
            List<BizCustomerShare> shares = shareMapper.selectList(
                    new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedToUserId, currentUserId));
            List<Long> sharedIds = shares.stream().map(BizCustomerShare::getCustomerId).toList();
            if (!sharedIds.isEmpty()) {
                wrapper.and(w -> w.eq(BizCustomer::getOwnerId, currentUserId)
                        .or().in(BizCustomer::getId, sharedIds));
            } else {
                wrapper.eq(BizCustomer::getOwnerId, currentUserId);
            }
        } else {
            // 管理员 - 数据范围过滤
            applyDataScope(wrapper);
            applyAutoShare(wrapper);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(BizCustomer::getCustomerName, keyword)
                    .or().like(BizCustomer::getCustomerCode, keyword));
        }
        if (StringUtils.hasText(industry)) {
            String[] arr = industry.split(",");
            if (arr.length > 1) wrapper.in(BizCustomer::getIndustry, arr);
            else wrapper.eq(BizCustomer::getIndustry, industry);
        }
        if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }
        if (regionId != null) {
            wrapper.eq(BizCustomer::getRegionId, regionId);
        }
        if (StringUtils.hasText(status)) {
            String[] arr = status.split(",");
            if (arr.length > 1) wrapper.in(BizCustomer::getStatus, arr);
            else wrapper.eq(BizCustomer::getStatus, status);
        } else {
            wrapper.ne(BizCustomer::getStatus, "sea");
        }
        if (ownerId != null) {
            wrapper.eq(BizCustomer::getOwnerId, ownerId);
        }
        if (StringUtils.hasText(devType)) {
            wrapper.eq(BizCustomer::getDevType, devType);
        }
        if (StringUtils.hasText(groupName)) {
            wrapper.eq(BizCustomer::getGroupName, groupName);
        }
        wrapper.orderByDesc(BizCustomer::getCreatedAt);

        IPage<BizCustomer> page = customerMapper.selectPage(new Page<>(current, size), wrapper);

        for (BizCustomer c : page.getRecords()) {
            fillCustomerNames(c);
        }
        return page;
    }

    private IPage<BizCustomer> pageSharedToMe(int current, int size, String keyword, String industry,
                                                Long officeId, Long regionId, String status, Long userId) {
        List<BizCustomerShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedToUserId, userId));
        List<Long> customerIds = shares.stream().map(BizCustomerShare::getCustomerId).toList();
        if (customerIds.isEmpty()) return new Page<>(current, size, 0);

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizCustomer::getId, customerIds).eq(BizCustomer::getDeleted, 0);
        applyCommonFilters(wrapper, keyword, industry, officeId, regionId, status);
        wrapper.orderByDesc(BizCustomer::getCreatedAt);
        IPage<BizCustomer> page = customerMapper.selectPage(new Page<>(current, size), wrapper);
        page.getRecords().forEach(this::fillCustomerNames);
        return page;
    }

    private IPage<BizCustomer> pageSharedByMe(int current, int size, String keyword, String industry,
                                                Long officeId, Long regionId, String status, Long userId) {
        List<BizCustomerShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedByUserId, userId));
        List<Long> customerIds = shares.stream().map(BizCustomerShare::getCustomerId).distinct().toList();
        if (customerIds.isEmpty()) return new Page<>(current, size, 0);

        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizCustomer::getId, customerIds).eq(BizCustomer::getDeleted, 0);
        applyCommonFilters(wrapper, keyword, industry, officeId, regionId, status);
        wrapper.orderByDesc(BizCustomer::getCreatedAt);
        IPage<BizCustomer> page = customerMapper.selectPage(new Page<>(current, size), wrapper);
        page.getRecords().forEach(this::fillCustomerNames);
        return page;
    }

    private void applyCommonFilters(LambdaQueryWrapper<BizCustomer> wrapper, String keyword,
                                     String industry, Long officeId, Long regionId, String status) {
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(BizCustomer::getCustomerName, keyword)
                    .or().like(BizCustomer::getCustomerCode, keyword));
        }
        if (StringUtils.hasText(industry)) wrapper.eq(BizCustomer::getIndustry, industry);
        if (officeId != null) wrapper.eq(BizCustomer::getOfficeId, officeId);
        if (regionId != null) wrapper.eq(BizCustomer::getRegionId, regionId);
        if (StringUtils.hasText(status)) {
            String[] arr = status.split(",");
            if (arr.length > 1) wrapper.in(BizCustomer::getStatus, arr);
            else wrapper.eq(BizCustomer::getStatus, status);
        } else {
            wrapper.ne(BizCustomer::getStatus, "sea");
        }
    }

    public Map<String, Long> listScopeCounts() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Long> counts = new HashMap<>();

        // 全部(受数据范围控制)
        LambdaQueryWrapper<BizCustomer> allW = new LambdaQueryWrapper<>();
        allW.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        applyDataScope(allW);
        counts.put("all", customerMapper.selectCount(allW));

        // 我的客户
        counts.put("mine", customerMapper.selectCount(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
                        .eq(BizCustomer::getOwnerId, userId)));

        // 我分享的
        long sharedByMe = shareMapper.selectCount(
                new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedByUserId, userId));
        counts.put("sharedByMe", sharedByMe);

        // 分享给我
        long sharedToMe = shareMapper.selectCount(
                new LambdaQueryWrapper<BizCustomerShare>().eq(BizCustomerShare::getSharedToUserId, userId));
        counts.put("sharedToMe", sharedToMe);

        return counts;
    }

    public List<Map<String, Object>> getSimpleUserList() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("realName", u.getRealName());
            result.add(m);
        }
        return result;
    }

    public BizCustomer getById(Long id) {
        BizCustomer customer = customerMapper.selectById(id);
        if (customer != null) {
            fillCustomerNames(customer);
        }
        return customer;
    }

    @Transactional
    public void create(BizCustomer customer) {
        customer.setCustomerCode(generateCustomerCode(customer));
        calculateExpireDates(customer);
        customer.setStatus("normal");
        customer.setProtectionStatus("active");
        if (customer.getOwnerId() == null) {
            customer.setOwnerId(SecurityUtils.getCurrentUserId());
        }
        if (customer.getOfficeId() == null) {
            customer.setOfficeId(SecurityUtils.getCurrentOfficeId());
        }
        customerMapper.insert(customer);
    }

    public boolean existsByName(String customerName) {
        return customerMapper.selectCount(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getCustomerName, customerName)
        ) > 0;
    }

    public CustomerImportResult importCustomers(MultipartFile file) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOfficeId = SecurityUtils.getCurrentOfficeId();

        CustomerImportResult result = new CustomerImportResult();
        CustomerImportListener listener = new CustomerImportListener(
                this, currentUserId, currentOfficeId, result);

        try {
            EasyExcel.read(file.getInputStream(), CustomerImportDTO.class, listener)
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        return listener.getResult();
    }

    private String generateCustomerCode(BizCustomer customer) {
        String rule = configService.getValue("customer.code.rule");
        if (rule == null) rule = "region_date_seq";

        // 获取大区编码: 优先用客户所属大区(regionId), 未选则回退到按办事处反查
        String regionCode = getRegionCode(customer.getRegionId() != null
                ? customer.getRegionId() : customer.getOfficeId());

        if ("region_seq".equals(rule)) {
            // 大区首字母-顺序号: NM-001
            String prefix = regionCode + "-";
            int maxSeq = customerMapper.selectMaxCodeByPrefix(prefix, prefix.length());
            return prefix + String.format("%03d", maxSeq + 1);
        } else {
            // region_date_seq: 大区首字母-年月-顺序号: NM-202606-001
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String prefix = regionCode + "-" + yearMonth + "-";
            int maxSeq = customerMapper.selectMaxCodeByPrefix(prefix, prefix.length());
            return prefix + String.format("%03d", maxSeq + 1);
        }
    }

    private String getRegionCode(Long officeId) {
        if (officeId == null) {
            officeId = SecurityUtils.getCurrentOfficeId();
        }
        // 查找办事处所属大区
        SysDept office = deptMapper.selectById(officeId);
        if (office != null) {
            // 如果本身就是大区(type=2)，直接用
            if (office.getDeptType() != null && office.getDeptType() == 2
                    && office.getDeptCode() != null) {
                return office.getDeptCode();
            }
            // 向上找大区
            if (office.getParentId() != null) {
                SysDept parent = deptMapper.selectById(office.getParentId());
                if (parent != null && parent.getDeptCode() != null) {
                    return parent.getDeptCode();
                }
            }
        }
        return "C"; // 兜底
    }

    @Transactional
    public void update(BizCustomer customer) {
        BizCustomer existing = customerMapper.selectById(customer.getId());
        if (existing == null) throw new BusinessException("客户不存在");
        // 权限检查：只有负责人和管理员可以编辑
        checkOwnerOrAdmin(existing, "只能编辑自己的客户");
        calculateExpireDates(customer);
        customerMapper.updateById(customer);
    }

    public void delete(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return;
        // 权限检查：只有负责人和管理员可以删除
        checkOwnerOrAdmin(existing, "只能删除自己的客户");
        customerMapper.deleteById(id);
    }

    /** 检查当前用户是否为客户负责人或系统管理员 */
    private void checkOwnerOrAdmin(BizCustomer customer, String msg) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean isAdmin = SecurityUtils.isAdmin();
        if (!isAdmin && (customer.getOwnerId() == null || !customer.getOwnerId().equals(userId))) {
            throw new org.springframework.security.access.AccessDeniedException(msg);
        }
    }

    // ====== 自动共享规则 ======

    /** 读取自动共享配置 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAutoShareConfig() {
        try {
            String json = configService.getValue("customer.auto_share");
            if (json == null || json.isBlank()) return java.util.Collections.emptyMap();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    /** 根据自动共享规则扩展可见的 owner ID */
    @SuppressWarnings("unchecked")
    private List<Long> getAutoShareOwnerIds() {
        Map<String, Object> cfg = getAutoShareConfig();
        if (cfg.isEmpty()) return java.util.Collections.emptyList();

        Long currentUserId = SecurityUtils.getCurrentUserId();
        java.util.Set<Long> ownerIds = new java.util.LinkedHashSet<>();
        ownerIds.add(currentUserId);

        // 规则1: 直接上级 → 看到直属下级的客户
        if (Boolean.TRUE.equals(cfg.get("superior"))) {
            java.util.List<SysUser> subordinates = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getManagerId, currentUserId)
                    .eq(SysUser::getStatus, 1)
                    .eq(SysUser::getDeleted, 0));
            for (SysUser sub : subordinates) {
                ownerIds.add(sub.getId());
            }
        }

        // 规则2: 管理员 → 返回空（不加过滤）
        if (Boolean.TRUE.equals(cfg.get("admin")) && SecurityUtils.isAdmin()) {
            return java.util.Collections.emptyList();
        }

        // 规则3: 指定角色 → 看到同组织内所有用户
        Object rolesObj = cfg.get("roles");
        if (rolesObj instanceof java.util.List && !((java.util.List<?>)rolesObj).isEmpty()) {
            java.util.List<String> currentRoles = SecurityUtils.getCurrentRoleCodes();
            for (Object r : (java.util.List<?>)rolesObj) {
                String roleId = String.valueOf(r);
                if (currentRoles.contains(roleId)) {
                    Long orgId = SecurityUtils.getCurrentOrgId();
                    if (orgId != null) {
                        java.util.List<SysUser> orgUsers = userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getOrgId, orgId)
                                .eq(SysUser::getStatus, 1)
                                .eq(SysUser::getDeleted, 0));
                        for (SysUser u : orgUsers) ownerIds.add(u.getId());
                    }
                    break;
                }
            }
        }

        // 规则4: 指定人员 → 不加过滤
        Object usersObj = cfg.get("users");
        if (usersObj instanceof java.util.List && !((java.util.List<?>)usersObj).isEmpty()) {
            for (Object uid : (java.util.List<?>)usersObj) {
                long targetId = uid instanceof Integer ? ((Integer)uid).longValue() : (Long)uid;
                if (currentUserId.equals(targetId)) {
                    return java.util.Collections.emptyList();
                }
            }
        }

        return new java.util.ArrayList<>(ownerIds);
    }

    /** 在客户查询时追加自动共享范围 */
    private void applyAutoShare(LambdaQueryWrapper<BizCustomer> wrapper) {
        java.util.List<Long> shareOwnerIds = getAutoShareOwnerIds();
        if (shareOwnerIds.isEmpty()) return;
        if (shareOwnerIds.size() <= 1) return;
        wrapper.in(BizCustomer::getOwnerId, shareOwnerIds);
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
        w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countInProtectionByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "active");
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiringByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expiring");
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    public long countExpiredByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, "expired");
        applyDataScope(w);
        return customerMapper.selectCount(w);
    }

    /** 本月新增客户数（受数据范围控制） */
    public long countNewThisMonth() {
        java.time.LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
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
            w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
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
            w.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea")
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
        list.forEach(this::fillCustomerNames);
        return list;
    }

    public Map<String, Object> getStatistics() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");

        // 数据范围过滤（统计也受范围控制）
        applyDataScope(wrapper);

        List<BizCustomer> customers = customerMapper.selectList(wrapper);

        Map<String, Long> industryStats = customers.stream()
                .filter(c -> c.getIndustry() != null)
                .collect(Collectors.groupingBy(BizCustomer::getIndustry, Collectors.counting()));

        Map<Long, Long> officeStats = customers.stream()
                .filter(c -> c.getOfficeId() != null)
                .collect(Collectors.groupingBy(BizCustomer::getOfficeId, Collectors.counting()));

        List<Map<String, Object>> officeList = new ArrayList<>();
        officeStats.forEach((oid, count) -> {
            Map<String, Object> item = new HashMap<>();
            var dept = deptMapper.selectById(oid);
            item.put("officeId", oid);
            item.put("officeName", dept != null ? dept.getDeptName() : "未知");
            item.put("count", count);
            officeList.add(item);
        });

        Map<String, Object> result = new HashMap<>();
        result.put("total", customers.size());
        result.put("industryStats", industryStats);
        result.put("officeStats", officeList);
        return result;
    }

    // ====== 数据范围核心方法（升级版：支持组织层级） ======

    private void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        String dataScope = SecurityUtils.getCurrentDataScope();
        Long userId = SecurityUtils.getCurrentUserId();
        Long deptId = SecurityUtils.getCurrentDeptId();
        Long orgId = SecurityUtils.getCurrentOrgId();

        switch (dataScope == null ? "self" : dataScope) {
            case "all":
                break;
            case "org_and_children":
                List<Long> orgAndChildIds = getDescendantOrgIds(orgId);
                orgAndChildIds.add(orgId);
                List<Long> orgUserIds = getUserIdsByOrgIds(orgAndChildIds);
                if (!orgUserIds.isEmpty()) {
                    wrapper.in(BizCustomer::getOwnerId, orgUserIds);
                } else {
                    wrapper.eq(BizCustomer::getOwnerId, userId);
                }
                break;
            case "org":
                List<Long> orgOnlyUserIds = getUserIdsByOrgIds(List.of(orgId));
                if (!orgOnlyUserIds.isEmpty()) {
                    wrapper.in(BizCustomer::getOwnerId, orgOnlyUserIds);
                } else {
                    wrapper.eq(BizCustomer::getOwnerId, userId);
                }
                break;
            case "dept_and_children":
                List<Long> deptIds = getDescendantDeptIds(deptId);
                deptIds.add(deptId);
                List<Long> userIds = getUserIdsByDeptIds(deptIds);
                if (!userIds.isEmpty()) {
                    wrapper.in(BizCustomer::getOwnerId, userIds);
                } else {
                    wrapper.eq(BizCustomer::getOwnerId, userId);
                }
                break;
            case "dept":
                List<Long> sameDeptUserIds = getUserIdsByDeptIds(List.of(deptId));
                if (!sameDeptUserIds.isEmpty()) {
                    wrapper.in(BizCustomer::getOwnerId, sameDeptUserIds);
                } else {
                    wrapper.eq(BizCustomer::getOwnerId, userId);
                }
                break;
            default:
                wrapper.eq(BizCustomer::getOwnerId, userId);
                break;
        }

        // 规则引擎：追加运行时 WHERE 条件（与枚举引擎 AND 合并）
        applyRuleEngineFilter(wrapper);
    }

    /** 注入权限规则引擎的 WHERE 条件（仅注入 target_entity='customer' 的规则） */
    private void applyRuleEngineFilter(LambdaQueryWrapper<BizCustomer> wrapper) {
        var user = SecurityUtils.getCurrentUser();
        if (user == null || user.getPermissionRules() == null || user.getPermissionRules().isEmpty()) return;
        // 按 target_entity 过滤，只取 customer 的 column_filter/org_filter/time_range 规则
        String ruleWhere = PermissionRuleEngine.evaluateForEntity(user.getPermissionRules(), user, "customer");
        if (ruleWhere != null && !ruleWhere.isEmpty()) {
            wrapper.apply(ruleWhere);
        }
    }

    /** 递归获取所有下级组织ID */
        private List<Long> getDescendantOrgIds(Long parentId) {
        try {
            return orgMapper.selectDescendantIds(parentId);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /** 获取指定组织列表中所有用户的ID */
    private List<Long> getUserIdsByOrgIds(List<Long> orgIds) {
        if (orgIds.isEmpty()) return new java.util.ArrayList<>();
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getOrgId, orgIds)
                        .eq(SysUser::getStatus, 1)
                        .eq(SysUser::getDeleted, 0));
        return users.stream().map(SysUser::getId).collect(java.util.stream.Collectors.toList());
    }
    /**
     * 递归获取所有下级部门ID
     */
    private List<Long> getDescendantDeptIds(Long parentId) {
        List<SysDept> allDepts = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getStatus, 1));
        List<Long> result = new ArrayList<>();
        collectChildren(allDepts, parentId, result);
        return result;
    }

    private void collectChildren(List<SysDept> all, Long parentId, List<Long> result) {
        for (SysDept dept : all) {
            if (Objects.equals(dept.getParentId(), parentId)) {
                result.add(dept.getId());
                collectChildren(all, dept.getId(), result);
            }
        }
    }

    /**
     * 获取指定部门列表中所有用户的ID
     */
    private List<Long> getUserIdsByDeptIds(List<Long> deptIds) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        w.in(SysUser::getDeptId, deptIds).eq(SysUser::getStatus, 1);
        return userMapper.selectList(w).stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());
    }

    private void calculateExpireDates(BizCustomer customer) {
        LocalDate baseDate = customer.getRegisterDate();
        if (customer.getLastInvoiceDate() != null) {
            if (baseDate == null || customer.getLastInvoiceDate().isAfter(baseDate)) {
                baseDate = customer.getLastInvoiceDate();
            }
        }
        if (baseDate != null) {
            int protectionDays = getConfigInt("customer.protection.days", 360);
            int adjustDays = getConfigInt("customer.adjust.days", 180);
            customer.setExpireDate(baseDate.plusDays(protectionDays));
            customer.setAdjustDate(baseDate.plusDays(adjustDays));
        }
    }

    private int getConfigInt(String key, int defaultValue) {
        String val = configService.getValue(key);
        if (val != null) {
            try { return Integer.parseInt(val.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private void fillCustomerNames(BizCustomer customer) {
        if (customer.getOwnerId() != null) {
            SysUser owner = userMapper.selectById(customer.getOwnerId());
            if (owner != null) customer.setOwnerName(owner.getRealName());
        }
        if (customer.getManagerId() != null) {
            SysUser manager = userMapper.selectById(customer.getManagerId());
            if (manager != null) customer.setManagerName(manager.getRealName());
        }
        if (customer.getOfficeId() != null) {
            var dept = deptMapper.selectById(customer.getOfficeId());
            if (dept != null) customer.setOfficeName(dept.getDeptName());
        }
        if (customer.getRegionId() != null) {
            var region = deptMapper.selectById(customer.getRegionId());
            if (region != null) customer.setRegionName(region.getDeptName());
        }
    }

    // ====== 开票记录 ======

    public List<BizInvoice> getInvoices(Long customerId) {
        return invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .orderByDesc(BizInvoice::getInvoiceDate));
    }

    public Map<String, Object> getInvoiceSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", invoiceMapper.countByCustomerId(customerId));
        summary.put("totalAmount", invoiceMapper.sumAmountByCustomerId(customerId));
        // 最近开票日期
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last("LIMIT 1"));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getInvoiceDate());
        return summary;
    }

    @Transactional
    public void addInvoice(Long customerId, BizInvoice invoice) {
        invoice.setCustomerId(customerId);
        invoice.setCreatedBy(SecurityUtils.getCurrentUserId());
        invoiceMapper.insert(invoice);
        refreshLastInvoiceDate(customerId);
        recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deleteInvoice(Long invoiceId) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) return;
        invoiceMapper.deleteById(invoiceId);
        refreshLastInvoiceDate(invoice.getCustomerId());
        recalculateCoreStatus(invoice.getCustomerId());
    }

    private void refreshLastInvoiceDate(Long customerId) {
        List<BizInvoice> latest = invoiceMapper.selectList(
                new LambdaQueryWrapper<BizInvoice>()
                        .eq(BizInvoice::getCustomerId, customerId)
                        .orderByDesc(BizInvoice::getInvoiceDate)
                        .last("LIMIT 1"));
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            customer.setLastInvoiceDate(latest.isEmpty() ? null : latest.get(0).getInvoiceDate());
            calculateExpireDates(customer);
            customerMapper.updateById(customer);
        }
    }

    // ====== 客户指标 ======

    public BizCustomerIndicator getIndicator(Long customerId) {
        return indicatorMapper.selectOne(
                new LambdaQueryWrapper<BizCustomerIndicator>()
                        .eq(BizCustomerIndicator::getCustomerId, customerId));
    }

    @Transactional
    public void saveIndicator(Long customerId, BizCustomerIndicator data) {
        data.setCustomerId(customerId);
        data.setUpdatedBy(SecurityUtils.getCurrentUserId());

        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            data.setId(existing.getId());
            indicatorMapper.updateById(data);
        } else {
            indicatorMapper.insert(data);
        }
        recalculateCoreStatus(customerId);
    }

    @Transactional
    public void saveRelationshipScore(Long customerId, int score) {
        BizCustomerIndicator existing = getIndicator(customerId);
        if (existing != null) {
            existing.setRelationshipScore(score);
            existing.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.updateById(existing);
        } else {
            BizCustomerIndicator ind = new BizCustomerIndicator();
            ind.setCustomerId(customerId);
            ind.setRelationshipScore(score);
            ind.setUpdatedBy(SecurityUtils.getCurrentUserId());
            indicatorMapper.insert(ind);
        }
    }

    private void recalculateCoreStatus(Long customerId) {
        BigDecimal invoiceAmount = invoiceMapper.sumAmountByCustomerId(customerId);
        BigDecimal paymentAmount = paymentMapper.sumAmountByCustomerId(customerId);
        BizCustomerIndicator indicator = getIndicator(customerId);
        if (indicator == null) return;

        int indicatorSum = zeroIfNull(indicator.getIndicator02()) + zeroIfNull(indicator.getIndicator03())
                + zeroIfNull(indicator.getIndicator04()) + zeroIfNull(indicator.getIndicator05())
                + zeroIfNull(indicator.getIndicator06());

        boolean thresholdMet = invoiceAmount.compareTo(new BigDecimal("100")) >= 0
                && paymentAmount.compareTo(new BigDecimal("100")) >= 0;
        boolean isCore = thresholdMet && indicatorSum >= 12;
        indicator.setIsCore(isCore ? 1 : 0);
        indicatorMapper.updateById(indicator);
    }

    private int zeroIfNull(Integer val) { return val == null ? 0 : val; }

    // ====== 客户分享 ======

    public List<BizCustomerShare> getShares(Long customerId) {
        List<BizCustomerShare> shares = shareMapper.selectList(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId));
        for (BizCustomerShare s : shares) {
            SysUser user = userMapper.selectById(s.getSharedToUserId());
            if (user != null) s.setSharedToUserName(user.getRealName());
        }
        return shares;
    }

    @Transactional
    public void addShares(Long customerId, List<Long> userIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) throw new BusinessException("客户不存在");
        String custName = customer.getCustomerName();
        SysUser sharer = userMapper.selectById(currentUserId);
        String sharerName = sharer != null ? sharer.getRealName() : "未知";

        for (Long uid : userIds) {
            // 检查是否已存在
            Long count = shareMapper.selectCount(
                    new LambdaQueryWrapper<BizCustomerShare>()
                            .eq(BizCustomerShare::getCustomerId, customerId)
                            .eq(BizCustomerShare::getSharedToUserId, uid));
            if (count == 0) {
                BizCustomerShare share = new BizCustomerShare();
                share.setCustomerId(customerId);
                share.setSharedToUserId(uid);
                share.setSharedByUserId(currentUserId);
                share.setCreatedAt(LocalDateTime.now());
                shareMapper.insert(share);

                // 1. 通知被分享人
                BizReminder r = new BizReminder();
                r.setCustomerId(customerId);
                r.setReminderType("share_to");
                r.setOwnerId(uid);
                r.setStatus("pending");
                r.setCreatedAt(LocalDateTime.now());
                reminderMapper.insert(r);

                // 2. 通知分享人
                BizReminder r2 = new BizReminder();
                r2.setCustomerId(customerId);
                r2.setReminderType("share_by");
                r2.setOwnerId(currentUserId);
                r2.setStatus("pending");
                r2.setCreatedAt(LocalDateTime.now());
                reminderMapper.insert(r2);
            }
        }

        // 3. 通知客户负责人（如果不是分享人自己）
        if (customer.getOwnerId() != null && !customer.getOwnerId().equals(currentUserId)) {
            BizReminder r3 = new BizReminder();
            r3.setCustomerId(customerId);
            r3.setReminderType("share_owner");
            r3.setOwnerId(customer.getOwnerId());
            r3.setStatus("pending");
            r3.setCreatedAt(LocalDateTime.now());
            reminderMapper.insert(r3);
        }
    }

    public void removeShare(Long customerId, Long userId) {
        shareMapper.delete(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId)
                        .eq(BizCustomerShare::getSharedToUserId, userId));
    }

    public void removeAllShares(Long customerId) {
        shareMapper.delete(
                new LambdaQueryWrapper<BizCustomerShare>()
                        .eq(BizCustomerShare::getCustomerId, customerId));
    }

    // ====== 联系人 ======

    public List<BizContact> getContacts(Long customerId) {
        return contactMapper.selectList(
                new LambdaQueryWrapper<BizContact>()
                        .eq(BizContact::getCustomerId, customerId)
                        .orderByAsc(BizContact::getId));
    }

    public void addContact(Long customerId, BizContact contact) {
        contact.setCustomerId(customerId);
        contactMapper.insert(contact);
    }

    public void updateContact(BizContact contact) {
        contactMapper.updateById(contact);
    }

    public void deleteContact(Long contactId) {
        contactMapper.deleteById(contactId);
    }

    // ====== 回款记录 ======

    public List<BizPayment> getPayments(Long customerId) {
        List<BizPayment> payments = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate));
        for (BizPayment p : payments) {
            if (p.getCreatedBy() != null) {
                SysUser user = userMapper.selectById(p.getCreatedBy());
                if (user != null) p.setCreatedByName(user.getRealName());
            }
        }
        return payments;
    }

    public Map<String, Object> getPaymentSummary(Long customerId) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", paymentMapper.countByCustomerId(customerId));
        summary.put("totalAmount", paymentMapper.sumAmountByCustomerId(customerId));
        List<BizPayment> latest = paymentMapper.selectList(
                new LambdaQueryWrapper<BizPayment>()
                        .eq(BizPayment::getCustomerId, customerId)
                        .orderByDesc(BizPayment::getPaymentDate)
                        .last("LIMIT 1"));
        summary.put("latestDate", latest.isEmpty() ? null : latest.get(0).getPaymentDate());
        return summary;
    }

    @Transactional
    public void addPayment(Long customerId, BizPayment payment) {
        payment.setCustomerId(customerId);
        payment.setCreatedBy(SecurityUtils.getCurrentUserId());
        paymentMapper.insert(payment);
        recalculateCoreStatus(customerId);
    }

    @Transactional
    public void deletePayment(Long paymentId) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        if (payment == null) return;
        paymentMapper.deleteById(paymentId);
        recalculateCoreStatus(payment.getCustomerId());
    }

    public java.util.Map<String, Object> getDuplicateInfo(String customerName) {
        try {
            return jdbc.queryForMap(
                "SELECT u.real_name AS createdBy, DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i') AS createdAt " +
                "FROM biz_customer c JOIN sys_user u ON c.owner_id = u.id " +
                "WHERE c.customer_name = ? AND c.deleted = 0 ORDER BY c.created_at DESC LIMIT 1", customerName);
        } catch (Exception e) {
            return null;
        }
    }
}
