package com.ycmn.module.sea.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.ycmn.common.BusinessException;
import com.ycmn.module.customer.dto.CustomerImportDTO;
import com.ycmn.module.customer.dto.CustomerImportResult;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.service.CustomerService;
import com.ycmn.module.sea.listener.SeaImportListener;
import org.springframework.web.multipart.MultipartFile;
import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaConfigMapper;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeaService {

    private final BizSeaPoolMapper seaPoolMapper;
    private final BizSeaConfigMapper seaConfigMapper;
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SeaConfigService seaConfigService;
    private final CustomerService customerService;

    /**
     * 当前用户可见且启用的公海池ID。
     * 【修复 2026-08-09 复核 B1】不再对 all 数据范围返回 null(不过滤)——否则禁用池(status=0)的
     * 存量客户仍会出现在管理员的公海列表/统计/可捞取,与"禁用池不在业务页展示"语义冲突。
     * all 用户 = 全部启用池(保留其全量可见性,但排除禁用池); 非 all 用户 = 可见且启用池。
     */
    private List<Long> visiblePoolIdsOrNull() {
        return seaConfigService.listVisible().stream().map(BizSeaConfig::getId).collect(Collectors.toList());
    }

    public IPage<BizSeaPool> page(int current, int size, String industry, Long officeId, String enterReason, String keyword, Long poolId, Long deptId, String status) {
        List<Long> visiblePoolIds = visiblePoolIdsOrNull();
        if (visiblePoolIds != null && visiblePoolIds.isEmpty()) {
            return new Page<>(current, size); // 无可见公海池
        }

        LambdaQueryWrapper<BizSeaPool> wrapper = new LambdaQueryWrapper<>();
        // 未指定 status 时默认排除已捞取（全部/待捞取/审批中）；指定 status 则精确过滤（保证分页与总数正确）
        if (StringUtils.hasText(status)) {
            wrapper.eq(BizSeaPool::getStatus, status);
        } else {
            wrapper.ne(BizSeaPool::getStatus, "claimed");
        }
        wrapper.orderByDesc(BizSeaPool::getEnterDate);
        // enterReason 是 biz_sea_pool 自身字段，直接在 DB 层过滤（保证分页与总数正确）
        if (StringUtils.hasText(enterReason)) {
            wrapper.eq(BizSeaPool::getEnterReason, enterReason);
        }
        if (visiblePoolIds != null) {
            wrapper.in(BizSeaPool::getPoolId, visiblePoolIds);
        }
        if (poolId != null) {
            if (visiblePoolIds != null && !visiblePoolIds.contains(poolId)) {
                return new Page<>(current, size); // 指定了不可见的池
            }
            wrapper.eq(BizSeaPool::getPoolId, poolId);
        }
        if (deptId != null) {
            // 部门筛选：客户所属组织在子树内（含全部子部门）
            java.util.List<Long> deptIds = com.ycmn.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId);
            java.util.List<Long> custIds = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().in(BizCustomer::getOfficeId, deptIds))
                    .stream().map(BizCustomer::getId).toList();
            if (custIds.isEmpty()) return new Page<>(current, size);
            wrapper.in(BizSeaPool::getCustomerId, custIds);
        }
        // S7: keyword/industry 是客户关联字段，下沉 SQL（客户 id 子查询）保证分页与 total 正确（原内存 removeIf 后 total 失真）
        if (StringUtils.hasText(keyword) || StringUtils.hasText(industry)) {
            LambdaQueryWrapper<BizCustomer> cw = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) cw.like(BizCustomer::getCustomerName, keyword);
            if (StringUtils.hasText(industry)) cw.eq(BizCustomer::getIndustry, industry);
            List<Long> custIds = customerMapper.selectList(cw).stream().map(BizCustomer::getId).toList();
            wrapper.in(BizSeaPool::getCustomerId, custIds.isEmpty() ? List.of(-1L) : custIds);
        }

        IPage<BizSeaPool> page = seaPoolMapper.selectPage(new Page<>(current, size), wrapper);

        for (BizSeaPool sp : page.getRecords()) {
            BizCustomer customer = customerMapper.selectById(sp.getCustomerId());
            if (customer != null) {
                sp.setCustomerName(customer.getCustomerName());
                sp.setCustomerCode(customer.getCustomerCode());
                sp.setIndustry(customer.getIndustry());
                if (customer.getOfficeId() != null) {
                    SysDept dept = deptMapper.selectById(customer.getOfficeId());
                    if (dept != null) sp.setRegion(dept.getDeptName());
                }
            }
            if (sp.getOriginalOwnerId() != null) {
                SysUser owner = userMapper.selectById(sp.getOriginalOwnerId());
                if (owner != null) sp.setOriginalOwnerName(owner.getRealName());
            }
        }

        return page;
    }

    public Map<String, Long> statistics() {
        Map<String, Long> stats = new HashMap<>();
        List<Long> visiblePoolIds = visiblePoolIdsOrNull();
        if (visiblePoolIds != null && visiblePoolIds.isEmpty()) {
            stats.put("total", 0L);
            stats.put("newThisMonth", 0L);
            stats.put("pendingApproval", 0L);
            stats.put("claimedThisMonth", 0L);
            return stats;
        }

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        stats.put("total", seaPoolMapper.selectCount(scoped(visiblePoolIds).eq(BizSeaPool::getStatus, "available")));
        stats.put("newThisMonth", seaPoolMapper.selectCount(scoped(visiblePoolIds).ge(BizSeaPool::getEnterDate, monthStart)));
        stats.put("pendingApproval", seaPoolMapper.selectCount(scoped(visiblePoolIds).eq(BizSeaPool::getStatus, "pending_approval")));
        stats.put("claimedThisMonth", seaPoolMapper.selectCount(scoped(visiblePoolIds)
                .eq(BizSeaPool::getStatus, "claimed").ge(BizSeaPool::getClaimDate, monthStart)));
        return stats;
    }

    /** 构造带可见池范围的查询条件(null 表示不限) */
    private LambdaQueryWrapper<BizSeaPool> scoped(List<Long> visiblePoolIds) {
        LambdaQueryWrapper<BizSeaPool> w = new LambdaQueryWrapper<>();
        if (visiblePoolIds != null) w.in(BizSeaPool::getPoolId, visiblePoolIds);
        return w;
    }

    @Transactional
    public void addToSea(Long customerId, String reason) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return;

        // 【修复 2026-08-09 B4】客户所属办事处必须能匹配到启用公海池,否则拒绝入池。
        // 与 CustomerController.release/CustomerExpireTask 的既有守卫对齐,
        // 统一收口在此处覆盖全部 4 个调用点(导入/离职/手动释放/定时释放),杜绝 pool_id=NULL 孤儿。
        Long poolId = matchSeaConfig(customer.getOfficeId());
        if (poolId == null) {
            throw new BusinessException("客户所属办事处未配置启用的公海池，无法进入公海");
        }

        BizSeaPool pool = new BizSeaPool();
        pool.setCustomerId(customerId);
        pool.setPoolId(poolId);
        pool.setOriginalOwnerId(customer.getOwnerId());
        pool.setEnterReason(reason);
        pool.setEnterDate(LocalDateTime.now());
        pool.setStatus("available");
        seaPoolMapper.insert(pool);

        // 更新客户状态
        customer.setStatus("sea");
        customer.setProtectionStatus("expired");
        customerMapper.updateById(customer);

        // 【修复 FEAT-20260804-SHARE-CLEANUP】客户进入公海即失去原负责人归属，此前不清理
        // biz_customer_share，导致原来被分享查看的同事在客户易主后仍长期保留访问权限。
        customerService.removeAllShares(customerId);
    }

    /**
     * 根据客户所属办事处ID匹配公海池配置。
     * 【修复 2026-08-09 B10】按 sortOrder 排序后取第一个命中,使"多池命中"结果确定
     * (sortOrder 小的优先);checkOfficeOverlap 已在配置层拦重叠,此排序兜底存量/手改数据歧义。
     */
    public Long matchSeaConfig(Long officeId) {
        if (officeId == null) return null;
        String officeIdStr = String.valueOf(officeId);
        List<BizSeaConfig> configs = seaConfigMapper.selectList(
                new LambdaQueryWrapper<BizSeaConfig>()
                        .eq(BizSeaConfig::getStatus, 1)
                        .orderByAsc(BizSeaConfig::getSortOrder)
                        .orderByAsc(BizSeaConfig::getId));
        for (BizSeaConfig config : configs) {
            if (config.getRegionIds() != null) {
                List<String> ids = Arrays.asList(config.getRegionIds().split(","));
                if (ids.stream().map(String::trim).anyMatch(id -> id.equals(officeIdStr))) {
                    return config.getId();
                }
            }
        }
        return null;
    }

    @Transactional
    public void completeClaim(Long customerId, Long claimUserId) {
        // 按 customer_id + 审批中 定位公海行（与 submit/reject 的查法一致）；
        // 历史脏数据可能出现同客户多条审批中记录，取最近一条兜底，避免 selectOne 抛异常
        BizSeaPool pool = seaPoolMapper.selectOne(new LambdaQueryWrapper<BizSeaPool>()
                .eq(BizSeaPool::getCustomerId, customerId)
                .eq(BizSeaPool::getStatus, "pending_approval")
                .orderByDesc(BizSeaPool::getEnterDate)
                .last("LIMIT 1"));
        if (pool == null) return;

        pool.setClaimUserId(claimUserId);
        pool.setClaimDate(LocalDateTime.now());
        pool.setStatus("claimed");
        seaPoolMapper.updateById(pool);

        // 更新客户归属
        BizCustomer customer = customerMapper.selectById(pool.getCustomerId());
        if (customer != null) {
            customer.setOwnerId(claimUserId);
            customer.setStatus("normal");
            customer.setProtectionStatus("active");
            customer.setRegisterDate(LocalDate.now());
            customer.setExpireDate(LocalDate.now().plusDays(360));
            customer.setAdjustDate(LocalDate.now().plusDays(180));
            customerMapper.updateById(customer);

            // 【修复 FEAT-20260804-SHARE-CLEANUP】客户被捞取后归属新负责人，清理原有分享记录，
            // 避免原负责人分享出去的同事在客户易主后仍长期保留访问权限
            customerService.removeAllShares(customerId);
        }
    }

    /**
     * 删除公海池中的客户。
     * 客户超期后从客户列表转入公海池，二者互斥；当客户处于"待捞取(available)"状态时可整体删除：
     * 逻辑删除该客户 + 物理删除对应公海池记录。已进入审批或已被捞取的记录不允许删除。
     */
    @Transactional
    public void deletePool(Long seaPoolId) {
        BizSeaPool pool = seaPoolMapper.selectById(seaPoolId);
        if (pool == null) {
            throw new BusinessException("公海记录不存在");
        }
        if (!"available".equals(pool.getStatus())) {
            throw new BusinessException("仅待捞取状态的客户可删除");
        }
        // 【安全修复 2026-08-09】池可见性校验：非 all 用户只能删除自己可见公海池的记录
        // （此前无任何 scope 判断，持 sea:pool:delete 者可删任意池客户——横向越权）
        List<Long> visiblePoolIds = visiblePoolIdsOrNull();
        if (visiblePoolIds != null && !visiblePoolIds.contains(pool.getPoolId())) {
            throw new BusinessException(403, "没有访问权限");
        }
        // 逻辑删除客户（biz_customer 有 deleted 列，走 MyBatis-Plus 逻辑删除）
        if (pool.getCustomerId() != null) {
            customerMapper.deleteById(pool.getCustomerId());
        }
        // 物理删除公海池记录（biz_sea_pool 无 deleted 列）
        seaPoolMapper.deleteById(seaPoolId);
    }

    /**
     * 公海池批量导入：模板与客户导入一致，每行新建客户并直接释放进公海。
     */
    public CustomerImportResult importToSea(MultipartFile file) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOfficeId = SecurityUtils.getCurrentOfficeId();

        CustomerImportResult result = new CustomerImportResult();
        SeaImportListener listener = new SeaImportListener(
                customerService, this, currentUserId, currentOfficeId, result);
        try {
            EasyExcel.read(file.getInputStream(), CustomerImportDTO.class, listener)
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }
        return result;
    }
}
