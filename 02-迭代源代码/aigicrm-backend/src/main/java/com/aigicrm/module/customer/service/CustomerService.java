package com.aigicrm.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
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

    /** 「所属集团」下拉候选返回上限（防大数据量下拉过长，超出的集团名仍可手工输入新建） */
    private static final int GROUP_NAME_LIMIT = 200;

    private final BizCustomerMapper customerMapper;
    private final BizCustomerLogMapper customerLogMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizCustomerShareMapper shareMapper;
    private final BizCustomerIndicatorMapper indicatorMapper;
    private final BizContactMapper contactMapper;
    private final BizPaymentMapper paymentMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final BizReminderMapper reminderMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService configService;
    private final com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    private final com.aigicrm.common.engine.CustomerAccessService customerAccessService;
    private final com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;

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
        } else if (false) { // disabled: all users use data scope (设计历史, 监督面板要求保留)
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
            if (arr.length > 1) wrapper.in(BizCustomer::getIndustry, (Object[]) arr);
            else wrapper.eq(BizCustomer::getIndustry, industry);
        }
        if (officeId != null) {
            wrapper.eq(BizCustomer::getOfficeId, officeId);
        }
        if (regionId != null) {
            wrapper.eq(BizCustomer::getRegionId, regionId);
        }
        if (deptId != null) {
            // 部门筛选：含该部门全部子部门（部门可无限嵌套）
            java.util.List<Long> deptIds = getDescendantDeptIds(deptId);
            deptIds.add(deptId);
            wrapper.in(BizCustomer::getDeptId, deptIds);
        }
        if (StringUtils.hasText(status)) {
            String[] arr = status.split(",");
            if (arr.length > 1) wrapper.in(BizCustomer::getStatus, (Object[]) arr);
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

        fillCustomerNames(page.getRecords());
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
        fillCustomerNames(page.getRecords());
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
        fillCustomerNames(page.getRecords());
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
            if (arr.length > 1) wrapper.in(BizCustomer::getStatus, (Object[]) arr);
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

    /**
     * 客户「所属集团」候选名（去重），供新增/编辑表单的可选可新建下拉使用。
     * 【2026-09-13】此前该字段为纯自由文本，同一集团出现多种写法，集团维度统计无法收敛。
     * 范围与客户列表一致（applyDataScope），只返回集团名本身，不暴露其它客户字段。
     * 注意：这里不能用 sys_group —— 那是「集团/法人租户配置」（权限体系顶层单元，如运营方自己的集团），
     * 与 biz_customer.group_name（客户所属集团，如"陕煤集团"）语义不同。
     */
    public List<String> listGroupNames() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<BizCustomer>()
                .select(BizCustomer::getGroupName)
                .isNotNull(BizCustomer::getGroupName)
                .ne(BizCustomer::getGroupName, "")
                .groupBy(BizCustomer::getGroupName)
                .orderByAsc(BizCustomer::getGroupName)
                .last("LIMIT " + GROUP_NAME_LIMIT);
        applyDataScope(wrapper);
        return customerMapper.selectList(wrapper).stream()
                .map(BizCustomer::getGroupName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public List<Map<String, Object>> getSimpleUserList() {
        // 【修复 2026-08-08 全量用户下拉】按数据范围(含系统级开关)过滤，不再返回全部用户
        // 【PR-01 P1-09 2026-09-18】all(=resolve null)对非 admin 收敛为当前集团成员；
        // 无集团归属直接空集（sys_user 非租户表，拦截器管不到，必须显式边界）。
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1).orderByAsc(SysUser::getId);
        List<Long> visible = scopeResolver.resolve(SecurityUtils.getCurrentUser());
        if (visible == null && !SecurityUtils.isAdmin()) {
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            visible = (g == null)
                    ? List.of()
                    : com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        }
        // 空集合显式空集返回（不依赖各版本对 IN() 空集合的拼接语义）
        if (visible != null && visible.isEmpty()) return List.of();
        if (visible != null) wrapper.in(SysUser::getId, visible);
        List<SysUser> users = userMapper.selectList(wrapper);
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
        // 【2026-08-14 404 防探测】不存在与无权访问统一文案, 消除存在性 oracle(GlobalExceptionHandler 按 code=404 转 HTTP 404)
        if (customer == null) {
            throw new BusinessException(404, "客户不存在或无权访问");
        }
        fillCustomerNames(java.util.List.of(customer));
        return customer;
    }

    @Transactional
    public void create(BizCustomer customer) {
        customer.setCustomerCode(generateCustomerCode(customer));
        calculateExpireDates(customer);
        customer.setStatus("normal");
        customer.setProtectionStatus("active");
        // 【M1 2026-08-10】代建规则: 仅 admin 可指定负责人(代建/挂单); 其他用户 ownerId 强制=当前用户——
        // 原代码非空即采用请求体 ownerId, 任何持 customer:add 者都可把客户挂到任意销售/办事处名下
        if (customer.getOwnerId() == null || !SecurityUtils.isAdmin()) {
            customer.setOwnerId(SecurityUtils.getCurrentUserId());
        }
        if (customer.getOfficeId() == null) {
            customer.setOfficeId(SecurityUtils.getCurrentOfficeId());
        }
        // 【P2-2 2026-08-11】ORG 锚点依赖 biz_customer.dept_id, 此前从不落库导致 ORG 范围配置不可用。
        // 客户归属部门=负责人(owner)的部门(与"数据范围按负责人部门归属"锚点决策一致)。
        syncCustomerDeptId(customer, customer.getOwnerId());
        customerMapper.insert(customer);
    }

    /**
     * 【P2-2 2026-08-11】同步客户 dept_id=负责人(owner)的部门, 使 ORG 锚点数据范围可用。
     * owner 缺失或 owner 无部门时显式清空 dept_id(null)——避免遗留旧负责人的部门导致
     * ORG 锚点数据范围错误关联(2026-08-12 A3 补齐: 原实现仅 owner 有部门才 set,
     * 无部门时旧 dept_id 残留, updateById 又只写非 null 字段, 需在调用处显式清空)。
     * 返回 owner 的部门(可为 null), 供调用方决定如何落库。
     */
    private Long syncCustomerDeptId(BizCustomer customer, Long ownerId) {
        if (ownerId == null) return null;
        try {
            com.aigicrm.module.system.entity.SysUser owner = userMapper.selectById(ownerId);
            Long deptId = owner != null ? owner.getDeptId() : null;
            customer.setDeptId(deptId);
            return deptId;
        } catch (Exception e) {
            // 部门同步失败不阻断创建(数据范围 ORG 分支降级为空/本人, 安全性不劣化)
            org.slf4j.LoggerFactory.getLogger(CustomerService.class)
                    .warn("同步客户归属部门失败 ownerId={}", ownerId, e);
            return null;
        }
    }

    /**
     * 【P2-2 2026-08-11】将客户 dept_id 强制落库(含 null 清空)。
     * MyBatis-Plus updateById 只写非 null 字段, 无法用 null 清空 dept_id;
     * 此方法用 LambdaUpdateWrapper.set 显式写(含 SET dept_id=NULL), 保证与 owner 部门精确一致。
     */
    private void persistCustomerDeptId(Long customerId, Long deptId) {
        if (customerId == null) return;
        customerMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizCustomer>()
                .eq(BizCustomer::getId, customerId)
                .set(BizCustomer::getDeptId, deptId));
    }

    public boolean existsByName(String customerName) {
        // 【修复 2026-08-10 P2-01】重名探测限定在当前用户数据范围内——
        // 此前任何登录用户可探测全公司任意客户名并获知创建人(跨范围信息泄露)
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getCustomerName, customerName);
        List<Long> scopeUserIds = getCurrentScopeUserIds();
        if (scopeUserIds != null) {
            w.in(BizCustomer::getOwnerId, scopeUserIds);
        }
        return customerMapper.selectCount(w) > 0;
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
        // 【2026-08-10 池级权限】编辑校验: 客户在公海池内用池编辑权限, 否则负责人/管理员(checkOwnerOrAdmin 语义由 canEdit 覆盖)
        if (!customerAccessService.canEdit(customer.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("只能编辑自己的客户");
        }
        // 【2026-08-10 安全】公海池内客户禁止经编辑接口改属主/状态——防止池编辑者直接 ownerId=self 自领客户绕过捞取审批。
        // 属主/状态变更只能走释放/捞取流程(release/claim)。请求未显式传该字段(null)视为不修改, 以库内值为准。
        if (customerAccessService.isInSeaPool(customer.getId())) {
            if (fieldChanged(existing.getOwnerId(), customer.getOwnerId())
                    || fieldChanged(existing.getStatus(), customer.getStatus())
                    || fieldChanged(existing.getProtectionStatus(), customer.getProtectionStatus())) {
                throw new BusinessException("公海池内客户不允许修改负责人/状态，请通过捞取/释放流程操作");
            }
            customer.setOwnerId(existing.getOwnerId());
            customer.setStatus(existing.getStatus());
            customer.setProtectionStatus(existing.getProtectionStatus());
        } else if (!SecurityUtils.isAdmin() && fieldChanged(existing.getOwnerId(), customer.getOwnerId())) {
            // 【M1 2026-08-10】非池客户负责人变更: 非 admin 只能走转移/审批流程, 防无审批转交
            throw new BusinessException("客户负责人变更请通过转移/审批流程操作");
        }
        protectCustomerDateFields(existing, customer);
        // 【P2-2 2026-08-12 A3】始终按最终负责人(请求显式或库内现有)重同步 dept_id——
        // 负责人变更(admin 转交)与负责人部门调整都跟随; owner 无部门时显式清空,
        // 不再遗留旧负责人部门(原实现仅 admin 且 owner 变更时才同步, 且空部门跳过)。
        Long effectiveOwnerId = customer.getOwnerId() != null ? customer.getOwnerId() : existing.getOwnerId();
        Long effectiveDeptId = syncCustomerDeptId(customer, effectiveOwnerId);
        calculateExpireDates(customer);
        customerMapper.updateById(customer);
        // updateById 只写非 null 字段: owner 无部门时显式清空 dept_id, 防旧部门残留
        if (effectiveDeptId == null && existing.getDeptId() != null) {
            persistCustomerDeptId(customer.getId(), null);
        }
    }

    /** 请求字段是否显式变更(null 视为未指定, 不判定为变更) */
    private boolean fieldChanged(Object existing, Object requested) {
        return requested != null && !requested.equals(existing);
    }

    private void protectCustomerDateFields(BizCustomer existing, BizCustomer requested) {
        if (canMaintainProtectedCustomerDates()) return;
        if (fieldChanged(existing.getRegisterDate(), requested.getRegisterDate())
                || fieldChanged(existing.getLastInvoiceDate(), requested.getLastInvoiceDate())) {
            throw new BusinessException("注册日和最近开票日录入后仅总监或行政可修改");
        }
        requested.setRegisterDate(existing.getRegisterDate());
        requested.setLastInvoiceDate(existing.getLastInvoiceDate());
    }

    private boolean canMaintainProtectedCustomerDates() {
        return SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("customer:date:edit");
    }

    public void delete(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return;
        // 【2026-08-10 权限体系重构】删除校验统一走 canDelete(池级PERM_DELETE / 非池按"删除范围"):
        // 取代旧的 checkOwnerOrAdmin(仅负责人/admin)——否则删除范围配置不生效
        if (!customerAccessService.canDelete(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能删除自己范围内的客户");
        }
        customerMapper.deleteById(id);
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

    /** 当日到期客户数（受数据范围控制） */
    public long countExpiringTodayByScope() {
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
        w.eq(BizCustomer::getDeleted, 0)
                .ne(BizCustomer::getStatus, "sea")
                .eq(BizCustomer::getExpireDate, java.time.LocalDate.now());
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
        fillCustomerNames(list);
        return list;
    }

    /**
     * 获取当前用户数据范围内的有效客户（未删除、非公海）
     * 【修复 FEAT-20260804-INDICATOR-SCOPE】供 StatisticsQueryService 等跨模块聚合报表复用，
     * 避免各处各自直接 selectList(null) 绕过数据范围导致全公司数据越权可见
     */
    public List<BizCustomer> getScopedActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        applyDataScope(wrapper);
        return customerMapper.selectList(wrapper);
    }

    /** 【2026-08-08 全员可见】全部启用客户（核心客户六项指标等公司级展示用，不做数据范围过滤） */
    public List<BizCustomer> getAllActiveCustomers() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");
        return customerMapper.selectList(wrapper);
    }

    public Map<String, Object> getStatistics() {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizCustomer::getDeleted, 0).ne(BizCustomer::getStatus, "sea");

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

    public Map<String, Object> customerRankings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incentive", customerIncentiveRanking());
        result.put("relationship", customerRelationshipRanking());
        result.put("core", customerCoreIndicatorRanking());
        return result;
    }

    private List<Map<String, Object>> customerIncentiveRanking() {
        // 【PR-01 P0-01 2026-09-18】原生 SQL 绕过租户拦截器，必须显式套集团条件；
        // 非 admin 无集团归属时 AND 1=0（fail-closed），admin 不过滤。
        java.util.List<Object> args = new java.util.ArrayList<>();
        String gc = groupCondition("c", args);
        // 注意：文本块内容从换行符之后开始，gc 与 GROUP BY 之间必须显式补 "\n"，
        // 否则拼出占位符紧贴关键字的语法错误（PR-22 活体验证 2026-09-19 发现）
        String sql = """
                SELECT c.id AS customerId, c.customer_name AS customerName, owner.real_name AS ownerName,
                       c.industry AS industry, COALESCE(SUM(ir.score), 0) AS totalScore,
                       COUNT(ir.id) AS uploadCount,
                       SUM(CASE WHEN ir.file_pre_id IS NOT NULL AND ir.file_pre_id <> '' AND ir.file_pre_id <> '[]' THEN 1 ELSE 0 END) AS preCount,
                       SUM(CASE WHEN ir.file_mid_id IS NOT NULL AND ir.file_mid_id <> '' AND ir.file_mid_id <> '[]' THEN 1 ELSE 0 END) AS midCount,
                       SUM(CASE WHEN ir.file_post_id IS NOT NULL AND ir.file_post_id <> '' AND ir.file_post_id <> '[]' THEN 1 ELSE 0 END) AS postCount,
                       SUM(CASE WHEN ir.file_track_id IS NOT NULL AND ir.file_track_id <> '' AND ir.file_track_id <> '[]' THEN 1 ELSE 0 END) AS trackCount
                FROM biz_incentive_record ir
                JOIN biz_customer c ON c.id = ir.customer_id AND c.deleted = 0
                LEFT JOIN sys_user owner ON owner.id = c.owner_id
                WHERE COALESCE(ir.score, 0) > 0""" + gc + "\n" + """
                GROUP BY c.id, c.customer_name, owner.real_name, c.industry
                ORDER BY totalScore DESC, uploadCount DESC, customerName ASC
                LIMIT 100
                """;
        return jdbc.queryForList(sql, args.toArray());
    }

    private List<Map<String, Object>> customerRelationshipRanking() {
        java.util.List<Object> args = new java.util.ArrayList<>();
        String gc = groupCondition("c", args);
        String sql = """
                SELECT c.id AS customerId, c.customer_name AS customerName, owner.real_name AS ownerName,
                       c.industry AS industry, COALESCE(i.relationship_score, 0) AS relationshipScore
                FROM biz_customer c
                JOIN biz_customer_indicator i ON i.customer_id = c.id
                LEFT JOIN sys_user owner ON owner.id = c.owner_id
                WHERE c.deleted = 0 AND COALESCE(i.relationship_score, 0) > 0""" + gc + "\n" + """
                ORDER BY relationshipScore DESC, customerName ASC
                LIMIT 100
                """;
        return jdbc.queryForList(sql, args.toArray());
    }

    private List<Map<String, Object>> customerCoreIndicatorRanking() {
        java.util.List<Object> args = new java.util.ArrayList<>();
        String gcOuter = groupCondition("c", args);
        // 子查询开票/回款同样按集团过滤（参数顺序与 SQL 出现顺序一致：inv、pay 在外层条件之前）
        String gcInv;
        String gcPay;
        if (com.aigicrm.security.SecurityUtils.isAdmin()) {
            gcInv = "";
            gcPay = "";
        } else {
            Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
            if (g == null) {
                gcInv = "";
                gcPay = "";
            } else {
                gcInv = " AND tenant_id = ?";
                gcPay = " AND tenant_id = ?";
                args.add(g);
                args.add(g);
            }
        }
        String sql = """
                SELECT c.id AS customerId, c.customer_name AS customerName, owner.real_name AS ownerName,
                       c.industry AS industry,
                       COALESCE(i.relationship_score, 0) AS relationshipScore,
                       COALESCE(i.indicator_02, 0) AS indicator02,
                       COALESCE(i.indicator_03, 0) AS indicator03,
                       COALESCE(i.indicator_04, 0) AS indicator04,
                       COALESCE(i.indicator_05, 0) AS indicator05,
                       COALESCE(inv.invoiceAmount, 0) AS invoiceAmount,
                       COALESCE(pay.paymentAmount, 0) AS paymentAmount,
                       (COALESCE(i.relationship_score, 0) + COALESCE(i.indicator_02, 0) + COALESCE(i.indicator_03, 0)
                        + COALESCE(i.indicator_04, 0) + COALESCE(i.indicator_05, 0)) AS totalScore,
                       CASE WHEN COALESCE(inv.invoiceAmount, 0) >= 100 AND COALESCE(pay.paymentAmount, 0) >= 100
                             AND (COALESCE(i.relationship_score, 0) + COALESCE(i.indicator_02, 0) + COALESCE(i.indicator_03, 0)
                                  + COALESCE(i.indicator_04, 0) + COALESCE(i.indicator_05, 0)) >= 12
                            THEN 1 ELSE 0 END AS coreCustomer
                FROM biz_customer c
                JOIN biz_customer_indicator i ON i.customer_id = c.id
                LEFT JOIN sys_user owner ON owner.id = c.owner_id
                LEFT JOIN (SELECT customer_id, SUM(amount) AS invoiceAmount FROM biz_invoice WHERE deleted = 0"""
                + gcInv + " GROUP BY customer_id) inv ON inv.customer_id = c.id\n"
                + "                LEFT JOIN (SELECT customer_id, SUM(payment_amount) AS paymentAmount FROM biz_payment WHERE deleted = 0"
                + gcPay + " GROUP BY customer_id) pay ON pay.customer_id = c.id\n"
                + "                WHERE c.deleted = 0" + gcOuter + "\n"
                + """
                  AND (COALESCE(i.relationship_score, 0) + COALESCE(i.indicator_02, 0) + COALESCE(i.indicator_03, 0)
                       + COALESCE(i.indicator_04, 0) + COALESCE(i.indicator_05, 0)) > 0
                ORDER BY totalScore DESC, coreCustomer DESC, customerName ASC
                LIMIT 100
                """;
        return jdbc.queryForList(sql, args.toArray());
    }

    /**
     * 【PR-01 P0-01 2026-09-18】原生 SQL 集团条件片段（参数化，groupId 来自会话非用户输入）：
     * admin → 空串；有集团 → " AND {alias}.tenant_id = ?" 并追加参数；
     * 无集团 → " AND 1=0"（fail-closed）。
     */
    private String groupCondition(String alias, java.util.List<Object> args) {
        if (com.aigicrm.security.SecurityUtils.isAdmin()) {
            return "";
        }
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (g == null) {
            return " AND 1=0";
        }
        args.add(g);
        return " AND " + alias + ".tenant_id = ?";
    }

    // ====== 数据范围核心方法（升级版：支持组织层级） ======

    private void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        // 【批② 2026-08-10 统一数据访问层】列表/统计/导出统一走 OrgScopeResolver.resolveScope(ScopeRequest),
        // 与详情/编辑/删除判定(CustomerAccessService.canAccess)共用同一链路, 根治 P1-05 口径分裂。
        // 旧 dataScope switch(org/org_and_children/dept_and_children/custom 各分支)已整体删除,
        // 全部由统一层的 OWNER/ORG 双锚点解析取代; 未配置 role_perm_scope 时回退 dataScope 默认语义。
        var scopeUser = SecurityUtils.getCurrentUser();
        if (scopeUser == null) {
            wrapper.eq(BizCustomer::getOwnerId, -1L);
            return;
        }
        if (SecurityUtils.isAdmin()) {
            applyRuleEngineFilter(wrapper);
            return;
        }
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(scopeUser)
                .module("customer")
                .action("view")
                .anchor(rolePermScopeService.anchorOf(scopeUser, "customer", "view"))
                .build();
        com.aigicrm.common.scope.ScopeDecision d = scopeResolver.resolveScope(req);
        if (d.isAll()) {
            // all: 本集团不过滤
        } else if (req.getAnchor() == com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG) {
            java.util.List<Long> deptIds = d.getVisibleDeptIds();
            if (deptIds != null && !deptIds.isEmpty()) wrapper.in(BizCustomer::getDeptId, deptIds);
            else if (deptIds != null) wrapper.eq(BizCustomer::getDeptId, -1L); // 空集合: 不可见
        } else {
            java.util.List<Long> userIds = d.getVisibleUserIds();
            if (userIds != null && !userIds.isEmpty()) wrapper.in(BizCustomer::getOwnerId, userIds);
            else if (userIds != null) wrapper.eq(BizCustomer::getOwnerId, -1L); // 空集合: 不可见
        }

        // 规则引擎：追加运行时 WHERE 条件（与统一层 AND 合并，追加条件层）
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

    /**
     * 解析当前用户按数据范围可见的用户 ID 集合；返回 null 表示全部可见（all 范围）
     * 【用途】统计/报表等跨实体查询复用与 applyDataScope 同一口径（self/dept/dept_and_children/org/custom）
     * 【修复 FEAT-20260804-DICT-DEPT-READ】经营分析/日报周报此前全量查询，销售员可见全公司数据
     */
    public List<Long> getCurrentScopeUserIds() {
        // 【批② 2026-08-10 统一数据访问层】统计/报表口径: OWNER 锚点走统一解析。
        // 语义与旧实现完全一致(未配置 role_perm_scope 时回退 dataScope 多角色并集+系统开关),
        // 但实现单一化——不再有第二套 switch 分支(旧 org/org_and_children/dept_and_children/custom
        // 分支已删除, 全部由 OrgScopeResolver.resolveScope 的 OWNER 解析覆盖)。
        var user = SecurityUtils.getCurrentUser();
        if (user == null) return List.of();
        if (SecurityUtils.isAdmin()) return null; // null=全部
        com.aigicrm.common.scope.ScopeRequest req = com.aigicrm.common.scope.ScopeRequest.builder()
                .user(user)
                .module("statistics")
                .action("view")
                // 【P2-3 2026-08-11】锚点与列表同源: 读 role_perm_scope.anchorOf 配置(未配置默认 OWNER),
                // 此前硬编码 OWNER 使统计口径在管理员配置 ORG 锚点后与列表脱节。
                .anchor(rolePermScopeService.anchorOf(user, "statistics", "view"))
                .build();
        return scopeResolver.resolveScope(req).getVisibleUserIds();
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

    private void calculateExpireDates(BizCustomer customer) {
        LocalDate baseDate = customer.getRegisterDate();
        // 合并嵌套 if（SonarLint S1066）：最近开票日晚于登记日(或无登记日)时以开票日为基准
        if (customer.getLastInvoiceDate() != null
                && (baseDate == null || customer.getLastInvoiceDate().isAfter(baseDate))) {
            baseDate = customer.getLastInvoiceDate();
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
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
                // 配置脏值回退默认值:解析失败返回 defaultValue(注明吞因,消除 S1166)
            }
        }
        return defaultValue;
    }

    /**
     * 批量填充客户关联名称
     *
     * 【需求】列表页展示负责人/上级/办事处/大区名称；原逐条查询导致 N+1（15 条=60 次查询）
     * 【方法】收集页面全部 userId/deptId，IN 批量查询两次，内存映射填充
     * 【业务规则】null id 跳过；查不到的名称保持 null（前端兜底显示空）
     */
    private void fillCustomerNames(List<BizCustomer> customers) {
        if (customers == null || customers.isEmpty()) return;

        Set<Long> userIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) userIds.add(c.getOwnerId());
            if (c.getManagerId() != null) userIds.add(c.getManagerId());
            if (c.getOfficeId() != null) deptIds.add(c.getOfficeId());
            if (c.getRegionId() != null) deptIds.add(c.getRegionId());
            if (c.getDeptId() != null) deptIds.add(c.getDeptId());
        }

        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectByIds(userIds)
                    .forEach(u -> userNames.put(u.getId(), u.getRealName()));
        }
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectByIds(deptIds)
                    .forEach(d -> deptNames.put(d.getId(), d.getDeptName()));
        }

        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) c.setOwnerName(userNames.get(c.getOwnerId()));
            if (c.getManagerId() != null) c.setManagerName(userNames.get(c.getManagerId()));
            if (c.getOfficeId() != null) c.setOfficeName(deptNames.get(c.getOfficeId()));
            if (c.getRegionId() != null) c.setRegionName(deptNames.get(c.getRegionId()));
            if (c.getDeptId() != null) c.setDeptName(deptNames.get(c.getDeptId()));
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
    public void deleteInvoice(Long customerId, Long invoiceId) {
        BizInvoice invoice = invoiceMapper.selectById(invoiceId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (invoice == null || !customerId.equals(invoice.getCustomerId())) {
            throw new BusinessException("发票不存在或不属于该客户");
        }
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

    /**
     * 【PR-05 2026-09-19】客户与本次客情指标同事务统一提交(新建)。
     * 原两步保存(建客户→存指标)在第二步失败时产生"部分成功、界面报保存失败"，
     * 用户重试可能重复建客户。组合方法单一事务：任一步失败整体回滚，界面失败即全未保存。
     */
    @Transactional
    public Long createWithIndicator(BizCustomer customer, BizCustomerIndicator indicator) {
        create(customer);
        if (indicator != null) {
            saveIndicator(customer.getId(), indicator);
        }
        return customer.getId();
    }

    /** 【PR-05】同上，编辑态组合提交。 */
    @Transactional
    public void updateWithIndicator(Long id, BizCustomer customer, BizCustomerIndicator indicator) {
        customer.setId(id);
        update(customer);
        if (indicator != null) {
            saveIndicator(id, indicator);
        }
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

        int indicatorSum = zeroIfNull(indicator.getRelationshipScore()) + zeroIfNull(indicator.getIndicator02())
                + zeroIfNull(indicator.getIndicator03()) + zeroIfNull(indicator.getIndicator04())
                + zeroIfNull(indicator.getIndicator05());

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

        for (Long uid : userIds) {
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

    @Transactional
    public void updateContact(Long customerId, Long contactId, BizContact contact) {
        BizContact existing = contactMapper.selectById(contactId);
        // 归属校验: 联系人必须属于请求路径中的客户, 防止跨客户改挂/删除(IDOR)
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            throw new BusinessException("联系人不存在或不属于该客户");
        }
        contact.setId(contactId);
        contact.setCustomerId(customerId);
        contactMapper.updateById(contact);
    }

    public void deleteContact(Long customerId, Long contactId) {
        BizContact existing = contactMapper.selectById(contactId);
        // 归属校验: 联系人必须属于请求路径中的客户, 防止跨客户删除(IDOR)
        if (existing == null || !customerId.equals(existing.getCustomerId())) {
            throw new BusinessException("联系人不存在或不属于该客户");
        }
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
    public void deletePayment(Long customerId, Long paymentId) {
        BizPayment payment = paymentMapper.selectById(paymentId);
        // 归属校验：记录必须属于请求路径中的客户，防止跨客户删除（IDOR）
        if (payment == null || !customerId.equals(payment.getCustomerId())) {
            throw new BusinessException("回款不存在或不属于该客户");
        }
        paymentMapper.deleteById(paymentId);
        recalculateCoreStatus(payment.getCustomerId());
    }

    public java.util.Map<String, Object> getDuplicateInfo(String customerName) {
        // 【修复 2026-08-10 P2-01】创建人/创建时间信息同样限定在数据范围内(与 existsByName 同口径)
        // 【PR-01 N-02 2026-09-18】原生 SQL 绕过租户拦截器：补 c.tenant_id 条件（biz_customer 在
        // 租户白名单内）；无集团归属直接返回 null（fail-closed，不泄露他租户创建人）。
        try {
            List<Long> scopeUserIds = getCurrentScopeUserIds();
            String tenantFrag;
            java.util.List<Object> tenantArgs = new java.util.ArrayList<>();
            if (!com.aigicrm.security.SecurityUtils.isAdmin()) {
                Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
                if (g == null) return null;
                tenantFrag = " AND c.tenant_id = ?";
                tenantArgs.add(g);
            } else {
                tenantFrag = "";
            }
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
