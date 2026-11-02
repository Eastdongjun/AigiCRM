package com.aigicrm.module.sea.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.sea.listener.SeaImportListener;
import org.springframework.web.multipart.MultipartFile;
import com.aigicrm.module.sea.entity.BizSeaConfig;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaConfigMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
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
    /** 【生命线 2026-11-02 裁决D】捞取/释放事件记录 */
    private final com.aigicrm.module.customer.service.CustomerEventService customerEventService;

    /**
     * 当前用户可见且启用的公海池ID——【P2-4 2026-10-19】永不返回 null(方法名随 B1 修复对齐:
     * 原 visiblePoolIdsOrNull 名字承诺"或 null 不过滤"是死契约, 诱导调用点写永假的 null 分支)。
     * 【修复 2026-08-09 复核 B1】不再对 all 数据范围返回 null(不过滤)——否则禁用池(status=0)的
     * 存量客户仍会出现在管理员的公海列表/统计/可捞取,与"禁用池不在业务页展示"语义冲突。
     * all 用户 = 全部启用池(保留其全量可见性,但排除禁用池); 非 all 用户 = 可见且启用池。
     */
    private List<Long> visiblePoolIds() {
        return seaConfigService.listVisible().stream().map(BizSeaConfig::getId).collect(Collectors.toList());
    }

    public IPage<BizSeaPool> page(int current, int size, String industry, Long officeId, String enterReason, String keyword, Long poolId, Long deptId, String status) {
        List<Long> visiblePoolIds = visiblePoolIds();
        if (visiblePoolIds.isEmpty()) {
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
        wrapper.in(BizSeaPool::getPoolId, visiblePoolIds);
        if (poolId != null) {
            if (!visiblePoolIds.contains(poolId)) {
                return new Page<>(current, size); // 指定了不可见的池
            }
            wrapper.eq(BizSeaPool::getPoolId, poolId);
        }
        if (deptId != null) {
            // 部门筛选：客户所属组织在子树内（含全部子部门）
            java.util.List<Long> deptIds = com.aigicrm.common.util.DeptTreeUtils.expandSubtree(deptMapper, deptId);
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
            // 【2026-08-10 池级权限】当前用户对该池的操作权限(前端按钮显示用)
            var currentUser = SecurityUtils.getCurrentUser();
            sp.setCanEdit(seaConfigService.hasPoolPerm(sp.getPoolId(), SeaConfigService.PERM_EDIT, currentUser));
            sp.setCanDelete(seaConfigService.hasPoolPerm(sp.getPoolId(), SeaConfigService.PERM_DELETE, currentUser));
            sp.setCanClaim(seaConfigService.hasPoolPerm(sp.getPoolId(), SeaConfigService.PERM_CLAIM, currentUser));
        }

        return page;
    }

    public Map<String, Long> statistics() {
        Map<String, Long> stats = new HashMap<>();
        List<Long> visiblePoolIds = visiblePoolIds();
        if (visiblePoolIds.isEmpty()) {
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

    /** 构造带可见池范围的查询条件(入参=visiblePoolIds() 产物, 永不为 null) */
    private LambdaQueryWrapper<BizSeaPool> scoped(List<Long> visiblePoolIds) {
        LambdaQueryWrapper<BizSeaPool> w = new LambdaQueryWrapper<>();
        w.in(BizSeaPool::getPoolId, visiblePoolIds);
        return w;
    }

    @Transactional
    public void addToSea(Long customerId, String reason) {
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) return;

        // 【B2 2026-09-22 幂等修复】已有未捞取记录(available/pending_approval)时不再重复入池——
        // 修复定时任务重跑/重复释放撞 uk_sea_pool_active(tenant_id,active_flag) 唯一键致整批中断的运行事故
        Long existingActive = seaPoolMapper.selectCount(
                new LambdaQueryWrapper<BizSeaPool>()
                        .eq(BizSeaPool::getCustomerId, customerId)
                        .ne(BizSeaPool::getStatus, "claimed"));
        if (existingActive != null && existingActive > 0) {
            if (!"sea".equals(customer.getStatus())) {
                customer.setStatus("sea");
                customer.setProtectionStatus("expired");
                customerMapper.updateById(customer);
            }
            return;
        }

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
        // 【生命线 2026-11-02 裁决D】释放事件(手动释放/到期自动/导入/离职四调用点统一经此入口)
        Long releaseOperator;
        try {
            releaseOperator = SecurityUtils.getCurrentUserId();
        } catch (Exception noRequestContext) {
            releaseOperator = null; // 凌晨任务自动入池无请求上下文, 事件显示"系统"
        }
        customerEventService.record(customerId,
                com.aigicrm.module.customer.service.CustomerEventService.TYPE_RELEASED,
                LocalDate.now(), "释放进公海" + (StringUtils.hasText(reason) ? "：" + reason : ""),
                null, null, releaseOperator);

        // 【修复 FEAT-20260804-SHARE-CLEANUP】客户进入公海即失去原负责人归属，此前不清理
        // biz_customer_share，导致原来被分享查看的同事在客户易主后仍长期保留访问权限。
        customerService.removeAllShares(customerId);
        // 【体检 P1 2026-10-12】入海即清全部类型 pending 提醒(INV-05 全量口径):
        // 池内客户的 expiring/invoice_* 死 pending 无合法处理路径, 是「—」死行供给源
        customerService.cleanPendingReminders(customerId);
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

        // 【B2 加固 2026-10-21·backend-customer 域移交】提交认领时 ApprovalService 以原子锁写入
        // claimUserId(available→pending_approval), 锁超时释放(CustomerExpireTask.releaseStaleClaimLocks)
        // 清申请人回 available。若本行现属其他申请人, 说明本审批对应的认领已被取代——原样覆写会把
        // 客户判给错误的人(错配认领链另一半)。fail-closed: 抛错回滚审批通过流程;
        // claimUserId 为空(历史脏数据)视为无主可认领, 照常完成。
        if (pool.getClaimUserId() != null && !pool.getClaimUserId().equals(claimUserId)) {
            throw new BusinessException("公海认领状态已变化(该客户已被他人申请)，当前审批无法完成，请刷新后重试");
        }

        pool.setClaimUserId(claimUserId);
        pool.setClaimDate(LocalDateTime.now());
        pool.setStatus("claimed");
        seaPoolMapper.updateById(pool);

        // 更新客户归属
        BizCustomer customer = customerMapper.selectById(pool.getCustomerId());
        if (customer != null) {
            // 【2026-11-02 裁决B/C 公式收敛】捞取日期独立字段 claim_date 参与三线公式(捞取线=捞取日+180
            // 短保护, 裁决B); 注册日不再被覆写(保留真实建档日语义, 覆写曾致原建档日丢失);
            // 天数/清延期/置空最近开票日/公式统一走 CustomerService.applyClaimReset 单一来源。
            LocalDate today = LocalDate.now();
            customer.setOwnerId(claimUserId);
            customer.setStatus("normal");
            customer.setProtectionStatus("active");
            customer.setLastInvoiceDate(null); // 实体同步置空, 公式按新周期计算(DB 落空在 applyClaimReset)
            customerService.applyClaimReset(customer, today);
            // 【生命线 2026-11-02 裁决D】捞取事件(操作人=捞取审批通过时的当前审批人)
            customerEventService.record(customerId,
                    com.aigicrm.module.customer.service.CustomerEventService.TYPE_CLAIMED, today,
                    "公海捞取成功, 180 天短保护期开始", null, today.toString(), claimUserId);

            // 【修复 FEAT-20260804-SHARE-CLEANUP】客户被捞取后归属新负责人，清理原有分享记录，
            // 避免原负责人分享出去的同事在客户易主后仍长期保留访问权限
            customerService.removeAllShares(customerId);
            // 【体检 P1 2026-10-12】捞取完成同样清全部类型 pending 提醒(INV-05 四路径之一);
            // 池内期间理论无新生成, 此处兜底入海时点之后的漏网死行
            customerService.cleanPendingReminders(customerId);
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
            // 【2026-08-14 404 防探测】单条语义接口统一文案, 不区分"不存在/状态不符/无权限", 消除存在性 oracle
            throw new BusinessException("资源不存在或无权访问");
        }
        if (!"available".equals(pool.getStatus())) {
            throw new BusinessException("资源不存在或无权访问");
        }
        // 【2026-08-10 池级权限】删除权限按池判定(admin 恒有)；同时保留可见性校验防横向越权
        if (!seaConfigService.hasPoolPerm(pool.getPoolId(), SeaConfigService.PERM_DELETE, SecurityUtils.getCurrentUser())) {
            throw new BusinessException(403, "资源不存在或无权访问");
        }
        List<Long> visiblePoolIds = visiblePoolIds();
        if (!visiblePoolIds.contains(pool.getPoolId())) {
            throw new BusinessException(403, "资源不存在或无权访问");
        }
        // 【审查 biz-3 2026-10-18】不能直接 customerMapper.deleteById 绕过客户删除统一流程
        // (分享/提醒清理由 CustomerService.delete 承担, 且它会拒绝活跃公海池行)。
        // 顺序: 业务保护 → 条件结束(物理删)池行 → 走客户删除统一内核(含分享/提醒/快照)。
        // 池行删除带 status='available' 条件: 与并发捞取(available→pending_approval)互斥,
        // affected=0 说明状态已变化, 返回统一 404 文案并回滚; 客户删除失败则池行随事务回滚。
        if (pool.getCustomerId() != null) {
            // 【B4 2026-09-22】复用客户删除业务保护: 有票款/在途审批的公海客户同样不可删, 消除入口差异
            customerService.assertDeletable(pool.getCustomerId());
        }
        int ended = seaPoolMapper.delete(new LambdaQueryWrapper<BizSeaPool>()
                .eq(BizSeaPool::getId, seaPoolId)
                .eq(BizSeaPool::getStatus, "available"));
        if (ended != 1) {
            throw new BusinessException("资源不存在或无权访问");
        }
        if (pool.getCustomerId() != null) {
            customerService.deleteFromSeaPool(pool.getCustomerId());
        }
    }

    /**
     * 公海池批量导入：模板与客户导入一致，每行新建客户并直接释放进公海。
     */
    public CustomerImportResult importToSea(MultipartFile file) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOfficeId = SecurityUtils.getCurrentOfficeId();

        // 【2026-08-10 池级权限】导入权限按目标池判定：导入人 office 路由到的池必须有导入权限
        Long targetPoolId = matchSeaConfig(currentOfficeId);
        if (targetPoolId == null) {
            throw new BusinessException("导入人所在办事处未配置启用的公海池");
        }
        if (!seaConfigService.hasPoolPerm(targetPoolId, SeaConfigService.PERM_IMPORT, SecurityUtils.getCurrentUser())) {
            throw new BusinessException(403, "没有该公海池的导入权限");
        }

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
