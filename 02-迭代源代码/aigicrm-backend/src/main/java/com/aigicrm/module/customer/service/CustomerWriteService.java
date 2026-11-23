package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.customer.dto.CustomerImportDTO;
import com.aigicrm.module.customer.dto.CustomerImportResult;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerStageAmount;
import com.aigicrm.module.customer.listener.CustomerImportListener;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RecycleBinService;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import com.alibaba.excel.EasyExcelFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 客户写侧：建/改/删/导入、编号生成、冻结字段与公海删除入口。
 *
 * <p>S5.3 从 {@link CustomerService} 抽出。删除/日期公式/审计/分享清理分别委托
 * Audit/Lifecycle/ContactShare；本类不反向依赖门面。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerWriteService {

    private static final String LIT_CUSTOMER = "customer";
    private static final String LIT_NORMAL = "normal";
    private static final String LIT_ACTIVE = "active";
    private static final String LIT_ARCHIVED = "archived";
    private static final String LIT_PENDING = "pending";
    private static final String LIT_X = "客户不存在";
    private final BizCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final BizSeaPoolMapper seaPoolMapper;
    private final BizApprovalMapper approvalMapper;
    private final BizCustomerStageAmountMapper stageAmountMapper;
    private final BizInvoiceMapper invoiceMapper;
    private final BizPaymentMapper paymentMapper;
    private final JdbcTemplate jdbc;
    private final SysConfigService configService;
    private final CustomerAccessService customerAccessService;
    private final CustomerLifecycleService customerLifecycleService;
    private final CustomerAuditService customerAuditService;
    private final CustomerContactShareService customerContactShareService;
    private final CustomerScopeService customerScopeService;
    private final CustomerEventService customerEventService;
    private final ObjectProvider<RecycleBinService> recycleBinServiceProvider;

    @Transactional
    public void create(BizCustomer customer) {
        // 【2026-11-17 客户名称全集团唯一】归一化后查重（含历史豁免行，防“新客绕过旧同名”），
        // 写入 name_norm；dup_exempt=0。展示名只 trim，不改变用户输入形态。
        // 兼容服务层单测/历史内部调用：name 为 null 时交由调用方/DB 约束处理（Controller/导入已前置校验）。
        if (customer.getCustomerName() != null) {
            String nameNorm = CustomerNameNormalizer.normalizeOrThrow(customer.getCustomerName());
            if (existsByNameGlobal(nameNorm, null)) {
                throw new BusinessException(400, "客户名称已存在");
            }
            customer.setCustomerName(CustomerNameNormalizer.displayName(customer.getCustomerName()));
            customer.setNameNorm(nameNorm);
            customer.setDupExempt(0);
        }
        // 【A1 2026-10-20】approved_extend_days 是审批域服务端派生字段, 新建一律强制置 0:
        // 请求体显式携带的值(接口直调可注入, 全仓 PC/小程序表单均无此字段)不得进入到期日公式。
        // 合法写入点仅三类: 审批通过累加(ApprovalService.executeApprovalAction)、捞取回私海清零
        // (SeaService.completeClaim)、开票基准前移清零(advanceLastInvoiceDate)。
        customer.setApprovedExtendDays(0);
        // 【D3 2026-10-18 用户裁决】新建/导入未填注册日时服务端兜底为当天,
        // 再由统一公式算出 expireDate/adjustDate——不硬拒绝,但不再产生无保护期基准的新客户。
        // 存量无日期客户按 D2 裁决维持现状, 不在此修复(仅新建路径)。
        if (customer.getRegisterDate() == null) {
            customer.setRegisterDate(LocalDate.now());
        }
        // 【裁决 2026-09-24】最近开票日=纯推导字段(最后一次非作废开票的日期): 创建时一律清空,
        // 不接受表单直带——历史开票请在客户财务页补录发票记录(自动驱动保护期,全程留痕)
        customer.setLastInvoiceDate(null);
        // 【2026-11-02 裁决B】捞取日期=审批域派生字段(仅 applyClaimReset 写入), 创建时一律清空
        customer.setClaimDate(null);
        customerLifecycleService.calculateExpireDates(customer);
        customer.setStatus(LIT_NORMAL);
        customer.setProtectionStatus(LIT_ACTIVE);
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
        // 【A3 2026-10-20/21 复核修复】取号+插入改为"集团行 FOR UPDATE 取号锁 + 唯一键冲突
        // 有限重试"(编号生成移到插入前一刻, prefix 只依赖 regionId/officeId, 与原首行生成等价):
        // MAX+1 取号原本无行锁, 并发建客户算出同一编号, 后提交方撞 uk_tenant_customer_code
        // 直接 500; 详见 insertWithCodeGenerationRetry 注释(死锁刻意不捕获: InnoDB 死锁回滚
        // 整个事务, 事务内重试会造成部分提交)。
        insertWithCodeGenerationRetry(customer);
        // 【生命线 2026-11-02 裁决D】建档事件(create 为创建唯一入口, 手工/导入/代建均经此)
        customerEventService.record(customer.getId(), CustomerEventService.TYPE_CREATED,
                customer.getRegisterDate() != null ? customer.getRegisterDate() : LocalDate.now(),
                "客户建档", null, customer.getCustomerCode(), SecurityUtils.getCurrentUserId());
    }

    /** 【A3 2026-10-20】取号重试上限: 首次 + 2 次退让(并发建客常见为 2~3 方同抢一号) */
    private static final int CODE_GEN_MAX_ATTEMPTS = 3;

    /**
     * 【A3 2026-10-20 取号行锁 + 有限重试；2026-10-21 复核修复】客户编号 MAX+1 取号并发兜底:
     * <ul>
     *   <li>【取号行锁】先对集团行 SELECT ... FOR UPDATE(取号前把同集团并发建客串行化,
     *       锁粒度=集团(tenant), 与唯一键 (tenant_id, customer_code) 同维; 写法沿用
     *       ApprovalService 的 jdbc 行锁先例)。同集团 contenders 在锁上排队, 取号读数即最新
     *       已提交值, 正常路径不再产生 1062。无集团上下文(null)时不加锁, 退让重试兜底。</li>
     *   <li>【有限重试】捕获 DuplicateKeyException(1062 唯一键冲突, MySQL 语句级失败,
     *       事务保持完整, 同事务内重试安全)后按 bump 递增退让重试, 上限
     *       {@link #CODE_GEN_MAX_ATTEMPTS} 次, 超限抛原始异常(不吞错)。
     *       <b>刻意不捕获 ConcurrencyFailureException(死锁 1213/锁等待超时 1205)</b>:
     *       InnoDB 死锁回滚的是整个事务而非单条语句, 在 @Transactional 内捕获继续执行
     *       会造成"前序写已回滚、后续写随提交重新生效"的部分提交, 故死锁必须原样传播
     *       (与修复前行为一致), 不做事务内重试。</li>
     *   <li>biz_customer 仅有 (tenant_id, customer_code) 一个唯一键(V2026.09.21.3),
     *       DuplicateKeyException 即编号冲突, 退让重试恰对症。</li>
     * </ul>
     * 真实并发集成测试受限于仓库无 Testcontainers/H2(审计 F7, P2): 已补真实多线程 +
     * 模拟唯一索引(READ COMMITTED 语义)的并发单测钉"各方全部成功且编号互不相同"。
     */
    private void insertWithCodeGenerationRetry(BizCustomer customer) {
        // 【A3 2026-10-21 取号行锁】集团行锁(同租户串行取号); 集团行不存在/无集团上下文时
        // 查询返回空或不执行, 无锁降级 → 由下方唯一键冲突退让重试兜底, 不阻断创建。
        Long tenantId = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (tenantId != null) {
            jdbc.queryForList("SELECT id FROM sys_group WHERE id = ? FOR UPDATE", Long.class, tenantId);
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= CODE_GEN_MAX_ATTEMPTS; attempt++) {
            try {
                customer.setCustomerCode(generateCustomerCode(customer, attempt - 1));
                customerMapper.insert(customer);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                last = e;
            }
        }
        if (last != null) throw last;
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
            org.slf4j.LoggerFactory.getLogger(CustomerWriteService.class)
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

    /** 【2026-11-17 客户名称全集团唯一】归一化查重：包含历史 dup_exempt 行（防新客绕过旧同名），
     *  排除自身 id；不做数据范围限制——唯一性是集团级不变量，不因调用者可见范围而改变。 */
    public boolean existsByNameGlobal(String customerName, Long excludeId) {
        String norm = CustomerNameNormalizer.normalize(customerName);
        if (norm == null || norm.isBlank()) return false;
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getNameNorm, norm);
        if (excludeId != null) {
            w.ne(BizCustomer::getId, excludeId);
        }
        return customerMapper.selectCount(w) > 0;
    }

    public boolean existsByName(String customerName) {
        return existsByName(customerName, null);
    }

    /** 【工作流复核确认修复】excludeId 非空时排除该客户(编辑场景查重不命中自身) */
    public boolean existsByName(String customerName, Long excludeId) {
        // 【修复 2026-08-10 P2-01】重名探测限定在当前用户数据范围内——
        // 此前任何登录用户可探测全公司任意客户名并获知创建人(跨范围信息泄露)
        LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<BizCustomer>()
                .eq(BizCustomer::getCustomerName, customerName);
        if (excludeId != null) {
            w.ne(BizCustomer::getId, excludeId);
        }
        List<Long> scopeUserIds = customerScopeService.getCurrentScopeUserIds();
        if (scopeUserIds != null) {
            // 【整改残余 B1 2026-10-21】空集守卫(fail-closed): A6 后 ORG 锚点+可见部门零在职
            // 用户时 getCurrentScopeUserIds 返回非 null 空集, w.in(空集) 会生成 IN () 触发 SQL
            // 500(建客查重路径)。空范围=无任何可见属主, 查重必然不命中, 直接短路不触库。
            if (scopeUserIds.isEmpty()) {
                return false;
            }
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
            EasyExcelFactory.read(file.getInputStream(), CustomerImportDTO.class, listener)
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        return listener.getResult();
    }

    /**
     * @param bump 取号退让偏移(A3 并发兜底): 0=首次取号 max+1; 重试时按 max+1+bump
     *             递增, 避开已被并发事务抢占的号码(见 {@link #insertWithCodeGenerationRetry})。
     */
    private String generateCustomerCode(BizCustomer customer, int bump) {
        String rule = configService.getValue("customer.code.rule");
        if (rule == null) rule = "region_date_seq";

        // 获取大区编码: 优先用客户所属大区(regionId), 未选则回退到按办事处反查
        String regionCode = getRegionCode(customer.getRegionId() != null
                ? customer.getRegionId() : customer.getOfficeId());
        Long tenantId = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();

        if ("region_seq".equals(rule)) {
            // 大区首字母-顺序号: NM-001
            String prefix = regionCode + "-";
            return prefix + String.format("%03d", currentMaxSeq(prefix, prefix.length(), tenantId) + 1 + bump);
        } else {
            // region_date_seq: 大区首字母-年月-顺序号: NM-202606-001
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String prefix = regionCode + "-" + yearMonth + "-";
            return prefix + String.format("%03d", currentMaxSeq(prefix, prefix.length(), tenantId) + 1 + bump);
        }
    }

    /**
     * 【A3 2026-10-21】当前前缀最大序号: 有集团上下文时走 FOR UPDATE 锁定读(读最新已提交值,
     * 不受本事务 REPEATABLE READ 旧快照影响——普通 SELECT 在同事务内取号会复用并发事务已
     * 提交的号码); 无集团上下文时退回普通读(无锁, 由调用方唯一键冲突退让重试兜底)。
     * 调用方须已持有同集团取号串行化锁({@link #insertWithCodeGenerationRetry})。
     */
    private int currentMaxSeq(String prefix, int prefixLen, Long tenantId) {
        if (tenantId != null) {
            Integer max = customerMapper.selectMaxCodeByPrefixForUpdate(prefix, prefixLen, tenantId);
            return max != null ? max : 0;
        }
        return customerMapper.selectMaxCodeByPrefix(prefix, prefixLen, tenantId);
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
        // 【A1 2026-10-20】读现值改用悲观锁(ApprovalService 延期执行同款先例):
        // update 全程复制库内 approved_extend_days, 若无行锁, "读到旧值→并发审批累加提交→
        // 本单 updateById 写回旧值"会丢一次累加; 锁内读改写保证与审批路径串行。
        BizCustomer existing = customerMapper.selectByIdForUpdate(customer.getId());
        if (existing == null) throw new BusinessException(LIT_X);
        assertUpdateAccess(existing, customer);
        applyNameValidation(existing, customer);
        boolean invoiceDateManuallyChanged = protectCustomerDateFields(existing, customer);
        // 【P2-2 2026-08-12 A3】始终按最终负责人(请求显式或库内现有)重同步 dept_id——
        // 负责人变更(admin 转交)与负责人部门调整都跟随; owner 无部门时显式清空,
        // 不再遗留旧负责人部门(原实现仅 admin 且 owner 变更时才同步, 且空部门跳过)。
        Long effectiveOwnerId = customer.getOwnerId() != null ? customer.getOwnerId() : existing.getOwnerId();
        Long effectiveDeptId = syncCustomerDeptId(customer, effectiveOwnerId);
        freezeUpdateFields(existing, customer, invoiceDateManuallyChanged);
        customerMapper.updateById(customer);
        // 【生命线 2026-11-02 裁决D】人工维护最近开票日事件(customer:invoice-date:edit 持码者操作)
        if (invoiceDateManuallyChanged) {
            customerEventService.record(customer.getId(), CustomerEventService.TYPE_INVOICE_MANUAL,
                    customer.getLastInvoiceDate(), "人工维护最近开票日(新基准重算保护期)",
                    existing.getLastInvoiceDate() != null ? existing.getLastInvoiceDate().toString() : null,
                    customer.getLastInvoiceDate() != null ? customer.getLastInvoiceDate().toString() : null,
                    SecurityUtils.getCurrentUserId());
        }
        // updateById 只写非 null 字段: owner 无部门时显式清空 dept_id, 防旧部门残留
        if (effectiveDeptId == null && existing.getDeptId() != null) {
            persistCustomerDeptId(customer.getId(), null);
        }
    }

    private void assertUpdateAccess(BizCustomer existing, BizCustomer customer) {
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
        } else if (!SecurityUtils.isAdmin()) {
            // 【M1 2026-08-10】非池客户负责人变更: 非 admin 只能走转移/审批流程, 防无审批转交
            if (fieldChanged(existing.getOwnerId(), customer.getOwnerId())) {
                throw new BusinessException("客户负责人变更请通过转移/审批流程操作");
            }
            // 【P2 2026-09-30 复审修复·流程字段冻结】非 admin 经通用编辑不得改状态或保护状态，
            // 状态迁移各有专门流程，通用编辑绕过即越权；
            // 值未变（表单原样回传）不算变更放行，与池分支口径一致。
            if (fieldChanged(existing.getStatus(), customer.getStatus())
                    || fieldChanged(existing.getProtectionStatus(), customer.getProtectionStatus())) {
                throw new BusinessException("客户状态/保护状态变更请通过归档、恢复、释放或保护期机制操作");
            }
        }
    }

    private void applyNameValidation(BizCustomer existing, BizCustomer customer) {
        // 【2026-11-17 客户名称全集团唯一】改名才查重；未改名保留历史 dup_exempt（祖父条款）。
        // 改名后 dup_exempt=0，进入严格唯一集合；归一化只影响 name_norm，展示名仅 trim。
        if (customer.getCustomerName() == null) return;
        String nameNorm = CustomerNameNormalizer.normalizeOrThrow(customer.getCustomerName());
        String existingNorm = existing.getNameNorm() != null
                ? existing.getNameNorm()
                : CustomerNameNormalizer.normalize(existing.getCustomerName());
        boolean nameChanged = existingNorm == null || !existingNorm.equals(nameNorm);
        if (nameChanged && existsByNameGlobal(nameNorm, customer.getId())) {
            throw new BusinessException(400, "客户名称已存在");
        }
        customer.setCustomerName(CustomerNameNormalizer.displayName(customer.getCustomerName()));
        customer.setNameNorm(nameNorm);
        int existingDupExempt = existing.getDupExempt() == null ? 0 : existing.getDupExempt();
        customer.setDupExempt(nameChanged ? 0 : existingDupExempt);
    }

    private void freezeUpdateFields(BizCustomer existing, BizCustomer customer, boolean invoiceDateManuallyChanged) {
        // 【A1 2026-10-20】延期/到期三字段冻结为服务端派生，普通编辑不可写也不可静默清零：
        // ① 已批延期一律以库内值覆盖请求值，避免表单不传时按 0 重算把延期清零，
        //   也防止显式传值任意拉长保护期；
        // ② 到期日/捞取到期日先剥掉请求体值，再统一由公式产生；
        //   无基准日的存量行保持空值，更新只写非空字段，请求体不可直写到期日。
        // 合法写入点：审批、捞取、开票前移、人工维护基准。
        // 用整数包装避免空值拆箱。
        customer.setApprovedExtendDays(invoiceDateManuallyChanged ? Integer.valueOf(0) : existing.getApprovedExtendDays());
        customer.setExpireDate(null);
        customer.setAdjustDate(null);
        // 【2026-11-02 裁决B】捞取日期=审批域派生字段(仅 applyClaimReset 写入), 通用编辑不得直改
        customer.setClaimDate(existing.getClaimDate());
        customerLifecycleService.calculateExpireDates(customer);
    }

    /** 请求字段是否显式变更(null 视为未指定, 不判定为变更) */
    private boolean fieldChanged(Object existing, Object requested) {
        return requested != null && !requested.equals(existing);
    }

    /**
     * @return true=本次请求人工改了最近开票日(调用方据此清零已批延期——新基准 360 天已覆盖旧延期,
     *         叠加会双重延长; 到期日统一由 update 末尾的 calculateExpireDates 重算, 此处不重复算)
     */
    private boolean protectCustomerDateFields(BizCustomer existing, BizCustomer requested) {
        // 【裁决 2026-11-02 用户名单】最近开票日人工维护: 持 customer:invoice-date:edit(行政/总监/admin
        // 绑定, 持码角色=行政/总监/admin, 名单以权限中心绑定为准)可直改——覆盖 2026-09-24"纯推导任何角色不可直改"裁决。
        // 人工设定=新基准语义(与开票前移同款); 自动推导"只前进"与其共存(更晚开票照常前移, 作废回算跟随剩余真实票)。
        boolean invoiceDateManuallyChanged = fieldChanged(existing.getLastInvoiceDate(), requested.getLastInvoiceDate());
        if (invoiceDateManuallyChanged) {
            FieldPermissionService.requireInvoiceDate();
        }
        if (!invoiceDateManuallyChanged) {
            requested.setLastInvoiceDate(existing.getLastInvoiceDate());
        }
        // 注册日仍为受控可改字段: customer:date:edit 权限(总监0/行政05/admin 绑定)
        if (!FieldPermissionService.canEditRegisterDate()) {
            if (fieldChanged(existing.getRegisterDate(), requested.getRegisterDate())) {
                FieldPermissionService.requireRegisterDate();
            }
            requested.setRegisterDate(existing.getRegisterDate());
        }
        return invoiceDateManuallyChanged;
    }

    /**
     * 【A5 2026-10-20】客户列表删除入口补事务: 删除内核是"软删客户→删分享→清 pending 提醒→
     * 删除快照"一组关联写, 原实现无事务, 任一步失败留下"客户已删、分享/提醒还在"的半删状态
     * (每语句自动提交); 公海入口 deleteFromSeaPool 已有 @Transactional, 两入口口径必须一致。
     * 自调用核查: 全仓仅 CustomerController 经代理调用本方法(grep 无 CustomerService 内部
     * this.delete 调用), 注解经代理生效; 内核 deleteCustomerRecord 是私有方法, 事务边界
     * 落在两个公共入口(delete/deleteFromSeaPool)上, 二者均已 @Transactional。
     */
    @Transactional
    public void delete(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return;
        // 【2026-08-10 权限体系重构】删除校验统一走 canDelete(池级PERM_DELETE / 非池按"删除范围"):
        // 取代旧的 checkOwnerOrAdmin(仅负责人/admin)——否则删除范围配置不生效
        if (!customerAccessService.canDelete(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能删除自己范围内的客户");
        }
        deleteCustomerRecord(id, existing);
    }

    /**
     * 【审查 biz-3 2026-10-18】公海池删除的客户侧统一入口。
     * 调用方(SeaService.deletePool)已完成池级 PERM_DELETE 与池行结束/锁；这里仍复跑
     * "无活跃池行 + 票款/在途审批保护 + 分享/提醒清理 + 删除快照"完整流程，
     * 与客户列表删除共用 {@link #deleteCustomerRecord}，避免公海入口绕过快照留痕。
     */
    @Transactional
    public void deleteFromSeaPool(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) return; // 已被并发删除: 幂等
        deleteCustomerRecord(id, existing);
    }

    /** 客户删除统一内核（调用方已完成权限/入口校验）: 池行守卫→业务保护→软删→分享/提醒→快照 */
    private void deleteCustomerRecord(Long id, BizCustomer existing) {
        // 【审计 data-2 修复 2026-10-18】客户列表删除必须尊重公海归属:存在未捞取池行
        // (available/pending_approval)时拒绝,引导到公海管理删除(按池级 sea:pool:delete 授权)。
        // 不在此静默删除池行——那会绕过池级权限,且让审批中的捞取单指向已删客户。
        // 【审查 biz-3 2026-10-18】公海入口调用前已结束池行，因此同一守卫仍成立且不用放开。
        Long activeSeaPoolRows = seaPoolMapper.selectCount(
                new LambdaQueryWrapper<BizSeaPool>()
                        .eq(BizSeaPool::getCustomerId, id)
                        .in(BizSeaPool::getStatus, "available", "pending_approval"));
        if (activeSeaPoolRows != null && activeSeaPoolRows > 0) {
            throw new BusinessException("该客户仍在公海池中（待捞取或审批中），请先在公海管理删除");
        }
        // 【议题五.3 裁决 2026-09-19】有业务数据的客户不允许删,转停用:
        assertDeletable(id);
        customerMapper.deleteById(id);
        com.aigicrm.module.system.service.RecycleBinService recycleBinService =
                recycleBinServiceProvider == null ? null : recycleBinServiceProvider.getIfAvailable();
        if (recycleBinService != null) {
            java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();
            snap.put("id", id);
            snap.put("customerName", existing.getCustomerName());
            snap.put("customerCode", existing.getCustomerCode());
            snap.put("ownerId", existing.getOwnerId());
            snap.put("deptId", existing.getDeptId());
            snap.put("officeId", existing.getOfficeId());
            snap.put("status", existing.getStatus());
            recycleBinService.record(LIT_CUSTOMER, id, snap, "用户删除", "user");
        }
        customerContactShareService.removeAllShares(id);
        // 【体检 P1 2026-10-12】INV-05 全量口径: 删除是终态, 该客户全部类型的 pending 提醒
        // (expiring/invoice_*)一并清理, 防死行重新积压(摸底 59 条的主供给源)
        customerContactShareService.cleanPendingReminders(id);
        // 【裁决 2026-09-24】客户删除留痕(三态不变量: 名下/公海/已删除, 删除是终态必须可追溯):
        // 完整快照落 sys_log, 管理员经 系统管理-系统日志 查看; 行本身 @TableLogic 逻辑删保留
        customerAuditService.logRecordDeletion("客户", "删除客户", LIT_CUSTOMER, id,
                "客户名=" + existing.getCustomerName() + ", 编码=" + existing.getCustomerCode()
                        + ", 负责人ID=" + existing.getOwnerId() + ", status=" + existing.getStatus()
                        + "/" + existing.getProtectionStatus() + ", deptId=" + existing.getDeptId());
    }

    /**
     * 【B4 2026-09-22 抽取复用】客户删除的业务保护统一入口: 有效开票/回款/在途审批任一存在即拒删。
     * 原本只服务于 delete(); 公海 deletePool 曾绕过此保护直接删客户(GPT 报告 BUS-P1-009),
     * 现由 SeaService 复用, 保证"同一删除动作同一规则"。
     */
    public void assertDeletable(Long id) {
        // 有效开票/回款/在途审批任一存在 → 拒绝(删前展示关联影响的最低实现)
        // 【PR-18 口径】此处计数刻意含已作废(voided)记录:作废仅取消金额效力,台账记录按
        // "保留原值、原因及操作者"留存,仍属客户的历史业务痕迹,不因作废而放开物理删除。
        // 【P0-1 2026-11-12 外部审计 CRITICAL 修复】补入现行开票/回款主载体 biz_customer_stage_amount
        // (2026-10-10 财务载体迁移后逐笔录入走阶段金额, legacy invoice/payment 对新建客户恒空——
        // 原判定使"有票款拒删"(INV-02)对现行链路失效; 对照组 RecycleService:569 早已计入, 两口收口一致)
        long stageCount = stageAmountMapper.selectCount(
                new LambdaQueryWrapper<BizCustomerStageAmount>()
                        .eq(BizCustomerStageAmount::getCustomerId, id));
        long invoiceCount = invoiceMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizInvoice>()
                        .eq(com.aigicrm.module.customer.entity.BizInvoice::getCustomerId, id));
        long paymentCount = paymentMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizPayment>()
                        .eq(com.aigicrm.module.customer.entity.BizPayment::getCustomerId, id));
        if (invoiceCount > 0 || paymentCount > 0 || stageCount > 0) {
            // 【文案修复 2026-10-09 用户反馈】原指引"请使用'停用'"是断头路——系统客户域没有
            // 叫"停用"的功能,实际停止跟进的入口是"归档"(议题五.4:默认隐藏+不参与统计提醒,可恢复)
            throw new BusinessException(String.format(
                    "该客户有 %d 笔开票、%d 笔回款、%d 条阶段金额记录,不允许删除;如需停止跟进请使用\"归档\"",
                    invoiceCount, paymentCount, stageCount));
        }
        // 【议题五.3 裁决 2026-09-20 补齐】在途审批(reviewing/pending)任一存在同样拒删——
        // 审批通过后的动作(延期/转移/捞取)作用于已删客户会产生悬挂回写
        long activeApprovals = approvalMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                        .eq(com.aigicrm.module.approval.entity.BizApproval::getCustomerId, id)
                        .in(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing", LIT_PENDING));
        if (activeApprovals > 0) {
            throw new BusinessException(String.format(
                    "该客户有 %d 条在途审批,不允许删除;请先完成或驳回审批", activeApprovals));
        }
    }

    /**
     * 【体检 P1 2026-10-12】在途审批守卫(释放口): 与 assertDeletable 同判据——
     * 释放进公海后, 在途的延期/转移审批若再通过会把客户拉回私海/盲改属主(双归属),
     * 须先处理审批再释放。到期自动入海(CustomerExpireTask)不经此口, 由执行侧守卫兜底。
     */
    public void assertNoActiveApprovals(Long id) {
        long activeApprovals = approvalMapper.selectCount(
                new LambdaQueryWrapper<com.aigicrm.module.approval.entity.BizApproval>()
                        .eq(com.aigicrm.module.approval.entity.BizApproval::getCustomerId, id)
                        .in(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing", LIT_PENDING));
        if (activeApprovals > 0) {
            throw new BusinessException(String.format(
                    "该客户有 %d 条在途审批,不允许释放进公海;请先完成或驳回审批", activeApprovals));
        }
    }

    /** 【议题五.4 裁决】归档:默认隐藏,可筛"含归档";归档客户不参与统计与到期提醒 */
    public void archive(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) throw new BusinessException(LIT_X);
        if (!customerAccessService.canEdit(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能归档自己范围内的客户");
        }
        existing.setStatus(LIT_ARCHIVED);
        customerMapper.updateById(existing);
        // 【体检 P1 2026-10-12】归档客户不参与提醒(议题五.4 裁决口径), 且 INV-05 全量口径:
        // 全部类型 pending 一并清(死 pending 判据同 V2026.09.23.8); 分享行保留备 restore,
        // 恢复后按各自规则重新生成, 不回填
        customerContactShareService.cleanPendingReminders(id);
    }

    /** 【议题五.4】恢复(从归档恢复正常) */
    public void restore(Long id) {
        BizCustomer existing = customerMapper.selectById(id);
        if (existing == null) throw new BusinessException(LIT_X);
        if (!LIT_ARCHIVED.equals(existing.getStatus())) {
            throw new BusinessException("该客户不在归档状态");
        }
        if (!customerAccessService.canEdit(id)) {
            throw new org.springframework.security.access.AccessDeniedException("只能恢复自己范围内的客户");
        }
        existing.setStatus(LIT_NORMAL);
        customerMapper.updateById(existing);
    }

}
