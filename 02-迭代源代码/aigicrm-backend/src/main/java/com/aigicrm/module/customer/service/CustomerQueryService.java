package com.aigicrm.module.customer.service;

import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerShare;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户查询面：分页/类型页签计数/导出、筛选条件拼装、名称回填、范围计数、集团候选。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出。数据范围/auto_share 复用
 * {@link CustomerScopeService}，本类不重写权限口径。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerQueryService {

    private static final String LIT_ARCHIVED = "archived";
    private static final int GROUP_NAME_LIMIT = 200;

    private final BizCustomerMapper customerMapper;
    private final BizCustomerShareMapper shareMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final CustomerScopeService customerScopeService;

    private void applyDataScopeAndAutoShare(LambdaQueryWrapper<BizCustomer> wrapper) {
        customerScopeService.applyDataScopeAndAutoShare(wrapper);
    }

    private void applyDataScope(LambdaQueryWrapper<BizCustomer> wrapper) {
        customerScopeService.applyDataScope(wrapper);
    }

    private List<Long> getDescendantDeptIds(Long parentId) {
        return customerScopeService.getDescendantDeptIds(parentId);
    }

    public IPage<BizCustomer> pageList(CustomerListFilter f, int current, int size) {
        LambdaQueryWrapper<BizCustomer> wrapper = buildListWrapper(f, f.devType(), null);
        wrapper.orderByDesc(BizCustomer::getCreatedAt);
        IPage<BizCustomer> page = customerMapper.selectPage(new Page<>(current, size), wrapper);
        fillCustomerNames(page.getRecords());
        return page;
    }

    /** 五个开发类型页签计数(键=页面页签名,含"全部"),与 pageList 同口径但不加 devType 条件;
     *  另附"预警中/已超期"两计数(状态覆盖统计,供 StatCards 概览条,替代原基于一次拉 500 条的本地计数) */
    public Map<String, Long> countByDevType(CustomerListFilter f) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<String> types = List.of("全部", "成熟创新", "突破开拓", "铺垫宣传", "新建改建扩建大修");
        for (String type : types) {
            String devType = "全部".equals(type) ? "" : type;
            result.put(type, customerMapper.selectCount(buildListWrapper(f, devType, null)));
        }
        result.put("预警中", customerMapper.selectCount(buildListWrapper(f, "", "warning")));
        result.put("已超期", customerMapper.selectCount(buildListWrapper(f, "", "expired")));
        return result;
    }

    /** 【M1 内存护栏 2026-10-08】导出行数上限:selectList 全量物化无上限,数据增长后单请求内存放大
     *  (customer:export 可反复触发);超限截断并日志留痕。业务上当前生产 604 行距 5 万护栏很远,
     *  分页/流式导出属后续优化,不在本次范围。 */
    private static final int EXPORT_MAX_ROWS = 50_000;

    /**
     * 导出清单(不分页,受数据范围控制):走 selectList 而非 selectPage——
     * 【修复 2026-10-05】分页拦截器已设 maxLimit=500,旧实现 page(1,10000) 会被静默截断
     * (生产客户数 604>500 即丢数据);selectList 不经分页拦截器,无此限制。
     * 【H1 2026-10-08】补回填名称:旧路径 page(1,10000) 内含 fillCustomerNames,改 selectList 后
     * 漏了同款调用,ownerName/managerName/officeName/regionName 为 exist=false 字段,
     * 导出 xlsx 负责人/管理上级/办事处/大区四列恒空(与 pageList 同款修复)。
     */
    public List<BizCustomer> exportList(CustomerListFilter f) {
        LambdaQueryWrapper<BizCustomer> wrapper = buildListWrapper(f, f.devType(), null);
        wrapper.orderByDesc(BizCustomer::getCreatedAt);
        List<BizCustomer> rows = customerMapper.selectList(wrapper);
        if (rows.size() > EXPORT_MAX_ROWS) {
            // 【M1】超限截断留痕(内存护栏):截断后再回填名称,避免白填被丢弃的行
            org.slf4j.LoggerFactory.getLogger(CustomerService.class)
                    .warn("导出结果 {} 行超过上限 {} 行,已截断(内存护栏)", rows.size(), EXPORT_MAX_ROWS);
            rows = List.copyOf(rows.subList(0, EXPORT_MAX_ROWS));
        }
        fillCustomerNames(rows);
        return rows;
    }

    /**
     * 统一条件拼装。devTypeOverride 非空时按指定类型过滤(计数场景逐类型调用),空串表示不加类型条件;
     * statusOverride 非空时覆盖 f.status()(计数场景的"预警中/已超期"状态口径);
     * scope 分支: shared_to_me/shared_by_me/mine/默认(数据范围+自动分享)。
     */
    private LambdaQueryWrapper<BizCustomer> buildListWrapper(CustomerListFilter f, String devTypeOverride, String statusOverride) {
        LambdaQueryWrapper<BizCustomer> wrapper = new LambdaQueryWrapper<>();
        if ("shared_to_me".equals(f.scope()) || "shared_by_me".equals(f.scope())) {
            Long uid = SecurityUtils.getCurrentUserId();
            List<BizCustomerShare> shares = shareMapper.selectList(new LambdaQueryWrapper<BizCustomerShare>()
                    .eq("shared_to_me".equals(f.scope()) ? BizCustomerShare::getSharedToUserId : BizCustomerShare::getSharedByUserId, uid));
            List<Long> ids = shares.stream().map(BizCustomerShare::getCustomerId)
                    .distinct().toList();
            if (ids.isEmpty()) {
                wrapper.apply("1 = 0"); // 无分享记录→恒空集(与旧实现返回空页一致)
            } else {
                wrapper.in(BizCustomer::getId, ids);
            }
        } else if ("mine".equals(f.scope())) {
            wrapper.eq(BizCustomer::getOwnerId, SecurityUtils.getCurrentUserId());
        } else {
            applyDataScopeAndAutoShare(wrapper);
        }
        applyFilterConditions(wrapper, f, devTypeOverride, statusOverride);
        return wrapper;
    }

    /** 全量筛选条件(基础+高级,原先散在 page() 与 applyCommonFilters 两处,现统一) */
    private void applyFilterConditions(LambdaQueryWrapper<BizCustomer> wrapper, CustomerListFilter f, String devType, String statusOverride) {
        wrapper.eq(BizCustomer::getDeleted, 0);
        applyStatusFilter(wrapper, f, statusOverride);
        applyKeywordFilter(wrapper, f.keyword());
        applyIndustryFilter(wrapper, f.industry());
        applyOrgFilters(wrapper, f);
        applyOwnerFilters(wrapper, f);
        applyProtectionFilter(wrapper, f.protectionStatus());
        // 【L2 2026-10-08】province/city LIKE 值经 escapeLike 转义 % _ 通配符
        if (StringUtils.hasText(f.province())) wrapper.like(BizCustomer::getProvince, escapeLike(f.province().trim()));
        if (StringUtils.hasText(f.city())) wrapper.like(BizCustomer::getCity, escapeLike(f.city().trim()));
        if (StringUtils.hasText(devType)) wrapper.eq(BizCustomer::getDevType, devType);
        if (StringUtils.hasText(f.groupName())) wrapper.eq(BizCustomer::getGroupName, f.groupName());
        applyDateRangeFilters(wrapper, f);
        applyOfficeIdsFilter(wrapper, f.officeIds());
    }

    private void applyStatusFilter(LambdaQueryWrapper<BizCustomer> wrapper, CustomerListFilter f, String statusOverride) {
        String status = statusOverride != null ? statusOverride : f.status();
        // 【议题五.4 裁决】归档客户默认隐藏;status=include_archived 时包含(显式传 'archived' 即只看归档)
        if ("include_archived".equals(status)) {
            // 【回归修复 2026-10-09】此处必须是 ne(sea) 而非 notIn(sea,archived): "显示已归档"=包含归档仅排公海
            wrapper.ne(BizCustomer::getStatus, "sea");
        } else if (StringUtils.hasText(status)) {
            applyStatusValue(wrapper, status);
        } else {
            wrapper.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        }
    }

    private void applyStatusValue(LambdaQueryWrapper<BizCustomer> wrapper, String status) {
        String[] arr = status.split(",");
        if (arr.length > 1) {
            wrapper.in(BizCustomer::getStatus, (Object[]) arr);
        } else if ("expired".equals(status)) {
            // 【口径统一 2026-10-09】「已超期」= expireDate<今日 且排除公海/归档(与统计页/工作台同判据)
            wrapper.lt(BizCustomer::getExpireDate, java.time.LocalDate.now())
                    .notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);
        } else {
            wrapper.eq(BizCustomer::getStatus, status);
        }
    }

    private void applyKeywordFilter(LambdaQueryWrapper<BizCustomer> wrapper, String keyword) {
        if (!StringUtils.hasText(keyword)) return;
        // 【议题五.1 裁决 2026-09-19】搜索增强:别名同权重 OR + 空格多词 AND
        String[] words = keyword.trim().split("\\s+");
        wrapper.and(w -> {
            for (String word : words) {
                w.and(inner -> inner.like(BizCustomer::getCustomerName, word)
                        .or().like(BizCustomer::getAlias, word)
                        .or().like(BizCustomer::getCustomerCode, word));
            }
        });
    }

    private void applyIndustryFilter(LambdaQueryWrapper<BizCustomer> wrapper, String industry) {
        if (!StringUtils.hasText(industry)) return;
        String[] arr = industry.split(",");
        if (arr.length > 1) wrapper.in(BizCustomer::getIndustry, (Object[]) arr);
        else wrapper.eq(BizCustomer::getIndustry, industry);
    }

    private void applyOrgFilters(LambdaQueryWrapper<BizCustomer> wrapper, CustomerListFilter f) {
        if (f.officeId() != null) wrapper.eq(BizCustomer::getOfficeId, f.officeId());
        if (f.regionId() != null) wrapper.eq(BizCustomer::getRegionId, f.regionId());
        if (f.deptId() != null) {
            // 部门筛选：含该部门全部子部门（部门可无限嵌套）
            java.util.List<Long> deptIds = getDescendantDeptIds(f.deptId());
            deptIds.add(f.deptId());
            wrapper.in(BizCustomer::getDeptId, deptIds);
        }
    }

    private void applyOwnerFilters(LambdaQueryWrapper<BizCustomer> wrapper, CustomerListFilter f) {
        if (f.ownerId() != null) wrapper.eq(BizCustomer::getOwnerId, f.ownerId());
        applyUserIdsFilter(wrapper, f.userIds());
        applyOwnerNameFilter(wrapper, f.ownerName());
    }

    private void applyUserIdsFilter(LambdaQueryWrapper<BizCustomer> wrapper, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        List<Long> ids = userIds.stream().distinct().toList();
        if (ids.size() == 1) wrapper.eq(BizCustomer::getOwnerId, ids.get(0));
        else wrapper.in(BizCustomer::getOwnerId, ids);
    }

    private void applyOwnerNameFilter(LambdaQueryWrapper<BizCustomer> wrapper, String ownerName) {
        if (!StringUtils.hasText(ownerName)) return;
        // 负责人按姓名模糊:名字无任何匹配用户时恒空集；LIKE 值经 escapeLike 转义
        List<Long> matched = userMapper.selectList(new LambdaQueryWrapper<com.aigicrm.module.system.entity.SysUser>()
                        .like(com.aigicrm.module.system.entity.SysUser::getRealName, escapeLike(ownerName.trim())))
                .stream().map(com.aigicrm.module.system.entity.SysUser::getId).distinct().toList();
        if (matched.isEmpty()) {
            wrapper.eq(BizCustomer::getOwnerId, -1L);
        } else if (matched.size() == 1) {
            wrapper.eq(BizCustomer::getOwnerId, matched.get(0));
        } else {
            wrapper.in(BizCustomer::getOwnerId, matched);
        }
    }

    private void applyProtectionFilter(LambdaQueryWrapper<BizCustomer> wrapper, String protectionStatus) {
        // 【对齐 PC 高级筛选 2026-09-21】protectionStatus 精确(逗号多值)
        if (!StringUtils.hasText(protectionStatus)) return;
        String[] arr = protectionStatus.split(",");
        if (arr.length > 1) wrapper.in(BizCustomer::getProtectionStatus, (Object[]) arr);
        else wrapper.eq(BizCustomer::getProtectionStatus, protectionStatus);
    }

    /** ---- 【容量加固 2026-10-05】高级筛选服务端化日期区间 ---- */
    private void applyDateRangeFilters(LambdaQueryWrapper<BizCustomer> wrapper, CustomerListFilter f) {
        if (f.regDateStart() != null) wrapper.ge(BizCustomer::getRegisterDate, f.regDateStart());
        if (f.regDateEnd() != null) wrapper.le(BizCustomer::getRegisterDate, f.regDateEnd());
        if (f.expDateStart() != null) wrapper.ge(BizCustomer::getExpireDate, f.expDateStart());
        if (f.expDateEnd() != null) wrapper.le(BizCustomer::getExpireDate, f.expDateEnd());
    }

    private void applyOfficeIdsFilter(LambdaQueryWrapper<BizCustomer> wrapper, List<Long> officeIds) {
        if (officeIds == null || officeIds.isEmpty()) return;
        // 对齐本地 orgIds 语义(所选组织单元下的客户): office 或 dept 命中即算
        wrapper.and(w -> w.in(BizCustomer::getOfficeId, officeIds)
                .or().in(BizCustomer::getDeptId, officeIds));
    }

    /**
     * 【L2 2026-10-08】LIKE 通配符转义:% 与 _ 前加反斜杠(先转义反斜杠自身,防止用户输入的
     * 反斜杠与后续转义符配对错位),用户输入按字面量匹配,不能借 % _ 放宽匹配面。
     * keyword 多词搜索(客户名/别名/编码 OR+AND)保持既有语义不动,不接入本转义。
     */
    private static String escapeLike(String value) {
        if (value == null) return null;
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    void fillCustomerNames(List<BizCustomer> customers) {
        if (customers == null || customers.isEmpty()) return;
        CustomerIdSets ids = collectCustomerIds(customers);
        Map<Long, String> userNames = loadUserNames(ids.userIds());
        Map<Long, String> deptNames = loadDeptNames(ids.deptIds());
        applyCustomerNames(customers, userNames, deptNames);
    }

    private record CustomerIdSets(Set<Long> userIds, Set<Long> deptIds) {}

    private CustomerIdSets collectCustomerIds(List<BizCustomer> customers) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> deptIds = new HashSet<>();
        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) userIds.add(c.getOwnerId());
            if (c.getManagerId() != null) userIds.add(c.getManagerId());
            if (c.getOfficeId() != null) deptIds.add(c.getOfficeId());
            if (c.getRegionId() != null) deptIds.add(c.getRegionId());
            if (c.getDeptId() != null) deptIds.add(c.getDeptId());
        }
        return new CustomerIdSets(userIds, deptIds);
    }

    private Map<Long, String> loadUserNames(Set<Long> userIds) {
        Map<Long, String> userNames = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectByIds(userIds)
                    .forEach(u -> userNames.put(u.getId(), u.getRealName()));
        }
        return userNames;
    }

    private Map<Long, String> loadDeptNames(Set<Long> deptIds) {
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            deptMapper.selectByIds(deptIds)
                    .forEach(d -> deptNames.put(d.getId(), d.getDeptName()));
        }
        return deptNames;
    }

    private void applyCustomerNames(List<BizCustomer> customers, Map<Long, String> userNames,
                                    Map<Long, String> deptNames) {
        for (BizCustomer c : customers) {
            if (c.getOwnerId() != null) c.setOwnerName(userNames.get(c.getOwnerId()));
            if (c.getManagerId() != null) c.setManagerName(userNames.get(c.getManagerId()));
            if (c.getOfficeId() != null) c.setOfficeName(deptNames.get(c.getOfficeId()));
            if (c.getRegionId() != null) c.setRegionName(deptNames.get(c.getRegionId()));
            if (c.getDeptId() != null) c.setDeptName(deptNames.get(c.getDeptId()));
        }
    }

    public Map<String, Long> listScopeCounts() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Long> counts = new HashMap<>();

        // 全部(受数据范围控制)
        LambdaQueryWrapper<BizCustomer> allW = new LambdaQueryWrapper<>();
        allW.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived
        applyDataScope(allW);
        counts.put("all", customerMapper.selectCount(allW));

        // 我的客户
        counts.put("mine", customerMapper.selectCount(
                new LambdaQueryWrapper<BizCustomer>()
                        .eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)
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

}
